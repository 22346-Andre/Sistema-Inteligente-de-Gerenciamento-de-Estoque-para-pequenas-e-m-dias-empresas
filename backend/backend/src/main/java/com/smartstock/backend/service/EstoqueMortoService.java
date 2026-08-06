package com.smartstock.backend.service;

import com.smartstock.backend.dto.EstoqueMortoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;


@Service
public class EstoqueMortoService {

    public static final int DIAS_PADRAO_ESTOQUE_MORTO = 90;
    private static final BigDecimal DESCONTO_QUEIMA = new BigDecimal("0.30"); // 30%

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    private Long getEmpresaIdLogada() {
        if (SecurityContextHolder.getContext().getAuthentication() == null ||
                !(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    /** Dias configurados pela empresa logada (ou o padrão, se ela nunca alterou). */
    public int getDiasParaEstoqueMortoDaEmpresaLogada() {
        return resolverDiasParaEstoqueMorto(getEmpresaIdLogada());
    }

    private int resolverDiasParaEstoqueMorto(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .map(Empresa::getDiasParaEstoqueMorto)
                .filter(dias -> dias != null && dias > 0)
                .orElse(DIAS_PADRAO_ESTOQUE_MORTO);
    }

    public List<EstoqueMortoDTO> listarEstoqueMorto() {
        return listarEstoqueMortoPorEmpresa(getEmpresaIdLogada());
    }

    public List<EstoqueMortoDTO> listarEstoqueMortoPorEmpresa(Long empresaId) {
        int diasParaConsiderarMorto = resolverDiasParaEstoqueMorto(empresaId);
        LocalDateTime dataLimite = LocalDateTime.now().minusDays(diasParaConsiderarMorto);
        List<Produto> parados = produtoRepository.findProdutosEncalhados(empresaId, dataLimite);

        List<EstoqueMortoDTO> lista = new ArrayList<>();
        LocalDateTime agora = LocalDateTime.now();

        for (Produto p : parados) {
            int quantidade = p.getQuantidade() != null ? p.getQuantidade() : 0;
            BigDecimal custo = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
            BigDecimal precoVenda = p.getPrecoVenda() != null ? p.getPrecoVenda() : BigDecimal.ZERO;

            EstoqueMortoDTO dto = new EstoqueMortoDTO();
            dto.setProdutoId(p.getId());
            dto.setNomeProduto(p.getNome());
            dto.setNomeFornecedor(p.getFornecedor() != null ? p.getFornecedor().getNome() : "Sem Fornecedor");
            dto.setQuantidadeParada(quantidade);
            dto.setValorUnitarioCusto(custo);
            dto.setValorParado(custo.multiply(BigDecimal.valueOf(quantidade)));

            LocalDateTime ultimaVenda = movimentacaoRepository.buscarDataUltimaVenda(p.getId());
            if (ultimaVenda == null) {
                //  nunca vendeu -> conta os dias a partir da DATA DE CADASTRO
                // em vez de deixar o contador nulo/parado em "Nunca vendeu".
                LocalDateTime referencia = p.getDataCriacao() != null ? p.getDataCriacao() : agora;
                long dias = ChronoUnit.DAYS.between(referencia, agora);
                dto.setDiasSemVenda((int) dias);
                dto.setDataUltimaVendaLabel("nunca vendeu · cadastrado há " + dias + " dias (" + formatarMesAno(referencia) + ")");
            } else {
                long dias = ChronoUnit.DAYS.between(ultimaVenda, agora);
                dto.setDiasSemVenda((int) dias);
                dto.setDataUltimaVendaLabel("há " + dias + " dias (" + formatarMesAno(ultimaVenda) + ")");
            }

            dto.setPrecoVendaAtual(precoVenda);

            
            BigDecimal precoQueimaCheio = precoVenda.multiply(BigDecimal.ONE.subtract(DESCONTO_QUEIMA))
                    .setScale(2, RoundingMode.HALF_UP);
            boolean precisouAjustar = precoQueimaCheio.compareTo(custo) < 0;
            BigDecimal precoQueimaFinal = precisouAjustar ? custo.setScale(2, RoundingMode.HALF_UP) : precoQueimaCheio;

            dto.setPrecoVendaQueima(precoQueimaFinal);
            dto.setMargemAjustada(precisouAjustar);

            lista.add(dto);
        }

        // Prioriza mostrando primeiro quem tem mais dinheiro parado
        lista.sort((a, b) -> b.getValorParado().compareTo(a.getValorParado()));
        return lista;
    }

    public BigDecimal calcularTotalCongelado(List<EstoqueMortoDTO> itens) {
        return itens.stream().map(EstoqueMortoDTO::getValorParado).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatarMesAno(LocalDateTime data) {
        String[] meses = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
        return meses[data.getMonthValue() - 1] + "/" + data.getYear();
    }

    public byte[] gerarPlanilhaQueimaCsv() {
        return gerarPlanilhaQueimaCsvPorEmpresa(getEmpresaIdLogada());
    }

    public byte[] gerarPlanilhaQueimaCsvPorEmpresa(Long empresaId) {
        List<EstoqueMortoDTO> itens = listarEstoqueMortoPorEmpresa(empresaId);
        StringBuilder csv = new StringBuilder();

        csv.append("PRODUTO;FORNECEDOR;QTD_PARADA;ULTIMA_VENDA;VALOR_CUSTO_UNIT;DINHEIRO_PARADO;PRECO_VENDA_ATUAL;PRECO_QUEIMA_30OFF;OBSERVACAO\n");

        for (EstoqueMortoDTO d : itens) {
            csv.append(d.getNomeProduto()).append(";")
                    .append(d.getNomeFornecedor()).append(";")
                    .append(d.getQuantidadeParada()).append(";")
                    .append(d.getDataUltimaVendaLabel()).append(";")
                    .append(d.getValorUnitarioCusto().toString().replace(".", ",")).append(";")
                    .append(d.getValorParado().toString().replace(".", ",")).append(";")
                    .append(d.getPrecoVendaAtual().toString().replace(".", ",")).append(";")
                    .append(d.getPrecoVendaQueima().toString().replace(".", ",")).append(";")
                    .append(d.isMargemAjustada() ? "Margem original menor que 30%; preco travado no custo (lucro zero) para nao vender no prejuizo" : "")
                    .append("\n");
        }

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(csvBytes, 0, finalBytes, bom.length, csvBytes.length);

        return finalBytes;
    }
}