package com.smartstock.backend.dto;

import lombok.Data;

@Data
public class GiroEstoqueDTO {
    private Long produtoId;
    private String nomeProduto;
    private Integer estoqueAtual;
    private Integer unidadesVendidasNoPeriodo;
    private Double giro; // unidadesVendidasNoPeriodo / estoqueAtual — quantas vezes o estoque girou no período
    private String classificacao; // ALTO, MEDIO ou BAIXO giro
}
