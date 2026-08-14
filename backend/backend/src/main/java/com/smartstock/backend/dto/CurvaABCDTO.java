package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CurvaABCDTO {
    private Long produtoId;
    private String nomeProduto;
    private Integer quantidade;
    private BigDecimal valorTotal; // Faturamento ou lucro no período, conforme o critério escolhido
    private Double percentualAcumulado; // Eixo Y do gráfico de Pareto: % do valor acumulado
    private Double percentualItensAcumulado; // Eixo X do gráfico de Pareto: % da quantidade de itens acumulada
    private String classe;
}