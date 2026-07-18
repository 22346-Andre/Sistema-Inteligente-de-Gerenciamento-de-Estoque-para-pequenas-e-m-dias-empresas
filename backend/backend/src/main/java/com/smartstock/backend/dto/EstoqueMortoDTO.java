package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EstoqueMortoDTO {
    private Long produtoId;
    private String nomeProduto;
    private String nomeFornecedor;
    private Integer quantidadeParada;
    private BigDecimal valorUnitarioCusto;
    private BigDecimal valorParado;          // custo × quantidade — o "dinheiro congelado" nesse item
    private Integer diasSemVenda;            // null = nunca vendeu desde que foi cadastrado
    private String dataUltimaVendaLabel;     // "Nunca vendeu" ou "há X dias" / mês, pra exibição direta
    private BigDecimal precoVendaAtual;
    private BigDecimal precoVendaQueima;     // sugestão com 30% de desconto sobre o preço de venda atual
}