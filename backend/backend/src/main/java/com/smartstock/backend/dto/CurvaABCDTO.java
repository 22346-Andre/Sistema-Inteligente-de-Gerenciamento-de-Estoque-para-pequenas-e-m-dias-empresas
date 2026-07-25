package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CurvaABCDTO {
    private Long produtoId;
    private String nomeProduto;
    private Integer quantidade;
    private BigDecimal valorTotal; // Faturamento, lucro ou volume no período, conforme o critério escolhido
    private Double percentualAcumulado;
    private String classe;
}