package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SugestaoCompraDTO {
    private Long produtoId;
    private String urgencia; // "URGENTE" ou "ATENCAO" (mantido p/ compatibilidade com a tela)
    private Double grauUrgencia; //  0 a 100, para barra de progresso / ordenação fina
    private String nomeProduto;
    private String nomeFornecedor;
    private Integer quantidadeAtual;
    private Integer estoqueMinimo;
    private Integer quantidadeSugerida;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private String telefoneFornecedor;
}