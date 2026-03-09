package com.smartstock.backend.service;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SugestaoCompraService {

    @Autowired
    private ProdutoRepository produtoRepository;

    //  Garante que o robô não tente roubar um login que não existe
    private Long getEmpresaIdLogada() {
        if (SecurityContextHolder.getContext().getAuthentication() == null ||
                !(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    // 1. Usado pela Tela (React) - Puxa do Login
    public List<SugestaoCompraDTO> listarSugestoes() {
        return listarSugestoesPorEmpresa(getEmpresaIdLogada());
    }

    // 2. Usado pelo Robô Automático - Puxa do ID que o robô mandar
    public List<SugestaoCompraDTO> listarSugestoesPorEmpresa(Long empresaId) {
        List<Produto> produtosCriticos = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresaId);
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtosCriticos) {
            SugestaoCompraDTO dto = new SugestaoCompraDTO();
            dto.setProdutoId(p.getId());
            dto.setNomeProduto(p.getNome());
            dto.setNomeFornecedor(p.getFornecedor() != null ? p.getFornecedor().getNome() : "Sem Fornecedor");
            dto.setTelefoneFornecedor(
                    p.getFornecedor() != null && p.getFornecedor().getTelefone() != null
                            ? p.getFornecedor().getTelefone()
                            : ""
            );

            int atual = p.getQuantidade() != null ? p.getQuantidade() : 0;
            int minimo = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;

            dto.setQuantidadeAtual(atual);
            dto.setEstoqueMinimo(minimo);

            int margemSeguranca = (int) Math.ceil(minimo * 0.5);
            int alvo = minimo + margemSeguranca;
            int quantidadeComprar = alvo - atual;
            if (quantidadeComprar <= 0) quantidadeComprar = 1;

            dto.setQuantidadeSugerida(quantidadeComprar);

            BigDecimal custo = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
            dto.setValorUnitario(custo);
            dto.setValorTotal(custo.multiply(new BigDecimal(quantidadeComprar)));

            dto.setUrgencia(atual == 0 ? "URGENTE" : "ATENCAO");

            sugestoes.add(dto);
        }
        return sugestoes;
    }

    // Usado pela Tela
    public byte[] gerarPlanilhaCsv() {
        return gerarPlanilhaCsvPorEmpresa(getEmpresaIdLogada());
    }

    // Usado pelo Robô
    public byte[] gerarPlanilhaCsvPorEmpresa(Long empresaId) {
        List<SugestaoCompraDTO> sugestoes = listarSugestoesPorEmpresa(empresaId);
        StringBuilder csv = new StringBuilder();

        csv.append("URGENCIA;PRODUTO;FORNECEDOR;QTD_ATUAL;ESTOQUE_MINIMO;QTD_COMPRAR;VALOR_UNITARIO;VALOR_TOTAL\n");

        for (SugestaoCompraDTO s : sugestoes) {
            csv.append(s.getUrgencia()).append(";")
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
}