package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SugestaoCompraDTO {
    private Long produtoId;
    private String urgencia; // "URGENTE" (zerado) ou "ATENÇÃO" (baixo)
    private String nomeProduto;
    private String nomeFornecedor;
    private Integer quantidadeAtual;
    private Integer estoqueMinimo;
    private Integer quantidadeSugerida;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private String telefoneFornecedor;
}