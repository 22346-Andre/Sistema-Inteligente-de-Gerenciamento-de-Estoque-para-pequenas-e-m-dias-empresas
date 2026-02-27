package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CurvaABCDTO {
    private String nomeProduto;
    private Integer quantidade;
    private BigDecimal valorTotal; // Quantidade * Preço
    private Double percentualAcumulado;
    private String classe; // 'A', 'B' ou 'C'
}