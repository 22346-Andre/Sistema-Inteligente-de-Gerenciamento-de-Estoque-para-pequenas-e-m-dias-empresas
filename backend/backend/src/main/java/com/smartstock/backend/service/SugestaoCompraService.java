package com.smartstock.backend.service;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartstock.backend.dto.SugestaoFornecedorDTO;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SugestaoCompraService {

    private static final int PRAZO_ENTREGA_PADRAO_DIAS = 7; // fallback enquanto Fornecedor não tiver o campo

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private FuzzyUrgenciaService fuzzyUrgenciaService;

    @Autowired
    private FornecedorService fornecedorService;

    private Long getEmpresaIdLogada() {
        if (SecurityContextHolder.getContext().getAuthentication() == null ||
                !(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    public List<SugestaoCompraDTO> listarSugestoes() {
        return listarSugestoesPorEmpresa(getEmpresaIdLogada());
    }

    public List<SugestaoCompraDTO> listarSugestoesPorEmpresa(Long empresaId) {
        List<Produto> produtosCriticos = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresaId);
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();
        LocalDateTime dataInicio = LocalDateTime.now().minusDays(30);

        // cache local do prazo observado por fornecedor — sem isso,
        // N produtos do MESMO fornecedor disparariam a mesma query de prazo
        // real N vezes seguidas na mesma requisição. Usa Optional porque
        // Map.computeIfAbsent NÃO cacheia quando a função devolve null (o que
        // aconteceria toda vez pra um fornecedor sem histórico suficiente).
        Map<Long, java.util.Optional<Double>> prazoObservadoPorFornecedor = new java.util.HashMap<>();

        for (Produto p : produtosCriticos) {
            int atual = p.getQuantidade() != null ? p.getQuantidade() : 0;
            int minimo = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;

            // --- Entradas do fuzzy ---
            double nivelEstoquePct = minimo > 0 ? ((double) atual / minimo) * 100.0 : 0.0;

            Integer saidas30dias = movimentacaoRepository.sumSaidasPorProdutoUltimosDias(p.getId(), dataInicio);
            double giroVendas = saidas30dias != null ? saidas30dias : 0.0;

            
            Integer prazoFornecedor = (p.getFornecedor() != null) ? p.getFornecedor().getPrazoEntregaDias() : null;
            Double prazoObservado = null;
            if (p.getFornecedor() != null) {
                Long fornecedorId = p.getFornecedor().getId();
                prazoObservado = prazoObservadoPorFornecedor
                        .computeIfAbsent(fornecedorId, id -> java.util.Optional.ofNullable(
                                fornecedorService.calcularPrazoEntregaObservado(id, empresaId)))
                        .orElse(null);
            }

            double prazoEntregaDias;
            String prazoEntregaOrigem;
            if (prazoObservado != null) {
                prazoEntregaDias = prazoObservado;
                prazoEntregaOrigem = "OBSERVADO";
            } else if (prazoFornecedor != null) {
                prazoEntregaDias = prazoFornecedor;
                prazoEntregaOrigem = "CONFIGURADO";
            } else {
                prazoEntregaDias = PRAZO_ENTREGA_PADRAO_DIAS;
                prazoEntregaOrigem = "PADRAO";
            }

            double grauUrgencia = fuzzyUrgenciaService.calcularUrgencia(nivelEstoquePct, giroVendas, prazoEntregaDias);

           
            if (atual == 0) {
                grauUrgencia = Math.max(grauUrgencia, 85.0);
            }

            // --- Monta o DTO ---
            SugestaoCompraDTO dto = new SugestaoCompraDTO();
            dto.setProdutoId(p.getId());
            dto.setNomeProduto(p.getNome());
            dto.setNomeFornecedor(p.getFornecedor() != null ? p.getFornecedor().getNome() : "Sem Fornecedor");
            dto.setTelefoneFornecedor(
                    p.getFornecedor() != null && p.getFornecedor().getTelefone() != null
                            ? p.getFornecedor().getTelefone()
                            : ""
            );
            dto.setQuantidadeAtual(atual);
            dto.setEstoqueMinimo(minimo);
            dto.setGrauUrgencia(Math.round(grauUrgencia * 10.0) / 10.0); // 1 casa decimal

            
            dto.setNivelEstoqueLabel(classificarNivelEstoque(nivelEstoquePct));
            dto.setGiroVendas((int) Math.round(giroVendas));
            dto.setGiroVendasLabel(classificarGiro(giroVendas));
            dto.setPrazoEntregaDias(Math.round(prazoEntregaDias * 10.0) / 10.0);
            dto.setPrazoEntregaLabel(classificarPrazo(prazoEntregaDias));
            dto.setPrazoEntregaOrigem(prazoEntregaOrigem);

            // Estoque zerado é sempre URGENTE, independente do fuzzy (regra de negócio dura)
            dto.setUrgencia(atual == 0 ? "URGENTE" : (grauUrgencia >= 55 ? "URGENTE" : "ATENCAO"));

            // --- Quantidade sugerida: base (repor até margem de segurança) + ajuste pela urgência ---
            int margemSeguranca = (int) Math.ceil(minimo * 0.5);
            int alvo = minimo + margemSeguranca;
            int quantidadeBase = Math.max(alvo - atual, 1);
            int ajusteUrgencia = (int) Math.round((grauUrgencia / 100.0) * minimo * 0.3); // até +30% do mínimo em casos críticos
            dto.setQuantidadeSugerida(quantidadeBase + ajusteUrgencia);

            BigDecimal custo = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
            dto.setValorUnitario(custo);
            dto.setValorTotal(custo.multiply(new BigDecimal(dto.getQuantidadeSugerida())));

            sugestoes.add(dto);
        }

        // Agora ordena pela urgência fuzzy real, não só por um enum fixo
        sugestoes.sort((s1, s2) -> Double.compare(s2.getGrauUrgencia(), s1.getGrauUrgencia()));

        return sugestoes;
    }

    public byte[] gerarPlanilhaCsv() {
        return gerarPlanilhaCsvPorEmpresa(getEmpresaIdLogada());
    }

    public byte[] gerarPlanilhaCsvPorEmpresa(Long empresaId) {
        List<SugestaoCompraDTO> sugestoes = listarSugestoesPorEmpresa(empresaId);
        StringBuilder csv = new StringBuilder();

        csv.append("URGENCIA;GRAU_URGENCIA;PRODUTO;FORNECEDOR;QTD_ATUAL;ESTOQUE_MINIMO;QTD_COMPRAR;VALOR_UNITARIO;VALOR_TOTAL\n");

        for (SugestaoCompraDTO s : sugestoes) {
            csv.append(s.getUrgencia()).append(";")
                    .append(String.valueOf(s.getGrauUrgencia()).replace(".", ",")).append(";")
                    .append(s.getNomeProduto()).append(";")
                    .append(s.getNomeFornecedor()).append(";")
                    .append(s.getQuantidadeAtual()).append(";")
                    .append(s.getEstoqueMinimo()).append(";")
                    .append(s.getQuantidadeSugerida()).append(";")
                    .append(s.getValorUnitario().toString().replace(".", ",")).append(";")
                    .append(s.getValorTotal().toString().replace(".", ",")).append("\n");
        }

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(csvBytes, 0, finalBytes, bom.length, csvBytes.length);

        return finalBytes;
    }

    public List<SugestaoFornecedorDTO> gerarTextosPorFornecedor(Long empresaId) {
    List<SugestaoCompraDTO> sugestoes = listarSugestoesPorEmpresa(empresaId);

    // Agrupa os itens críticos por fornecedor, mantendo a ordem de urgência já definida
    Map<String, List<SugestaoCompraDTO>> porFornecedor = sugestoes.stream()
            .collect(Collectors.groupingBy(
                    SugestaoCompraDTO::getNomeFornecedor,
                    LinkedHashMap::new,
                    Collectors.toList()
            ));

    List<SugestaoFornecedorDTO> resultado = new ArrayList<>();

    for (Map.Entry<String, List<SugestaoCompraDTO>> entry : porFornecedor.entrySet()) {
        String fornecedor = entry.getKey();
        List<SugestaoCompraDTO> itens = entry.getValue();
        String telefone = itens.get(0).getTelefoneFornecedor();

        StringBuilder texto = new StringBuilder();
        texto.append("Olá, ").append(fornecedor).append("! Tudo bem?\n\n");
        texto.append("Gostaríamos de fazer um pedido de reposição:\n\n");

        for (SugestaoCompraDTO item : itens) {
            texto.append("• ").append(item.getNomeProduto())
                 .append(" — ").append(item.getQuantidadeSugerida()).append(" un.");
            if ("URGENTE".equals(item.getUrgencia())) {
                texto.append(" (URGENTE)");
            }
            texto.append("\n");
        }

        texto.append("\nPodem confirmar disponibilidade e prazo de entrega?\n");
        texto.append("Obrigado!");

        String linkWhatsApp = null;
        if (telefone != null && !telefone.isBlank()) {
            String telefoneLimpo = telefone.replaceAll("[^0-9]", "");
            String textoCodificado = URLEncoder.encode(texto.toString(), StandardCharsets.UTF_8);
            linkWhatsApp = "https://wa.me/" + telefoneLimpo + "?text=" + textoCodificado;
        }

        resultado.add(new SugestaoFornecedorDTO(fornecedor, telefone, texto.toString(), linkWhatsApp, itens));
    }

    return resultado;
  }

    // ========================================================================
    // classificadores de rótulo pra explicabilidade na tela (tooltip
    // "Por que comprar?"). Os pontos de corte usados aqui são os mesmos picos
    // das funções de pertinência do FuzzyUrgenciaService — não recalculam
    // nada do motor fuzzy, só traduzem os valores numéricos brutos (nível de
    // estoque em %, giro em unidades/mês, prazo em dias) num rótulo legível
    // pro gestor. O grau de urgência em si continua saindo 100% do fuzzy.
    // ========================================================================
    private String classificarNivelEstoque(double percentualDoMinimo) {
        if (percentualDoMinimo < 50) return "Baixo";
        if (percentualDoMinimo <= 150) return "Adequado";
        return "Alto";
    }

    private String classificarGiro(double unidadesUltimos30Dias) {
        if (unidadesUltimos30Dias < 30) return "Lento";
        if (unidadesUltimos30Dias <= 60) return "Moderado";
        return "Rápido";
    }

    private String classificarPrazo(double dias) {
        if (dias <= 5) return "Rápido";
        if (dias <= 15) return "Aceitável";
        return "Demorado";
    }
}