package com.smartstock.backend.dto;

import lombok.Data;

/**
 * Cruzamento das classes de Faturamento e Lucratividade do mesmo produto —
 * é comum um produto ter classificações opostas nas duas curvas (ex: "A" em
 * Faturamento mas "C" em Lucratividade), o que é um insight de gestão real
 * (o "Campeão de Vendas" que não dá lucro, ou o "Motor de Lucro" que vende
 * pouco) e passa despercebido se as duas curvas só forem olhadas separadas.
 */
@Data
public class MatrizAbcDTO {
    private Long produtoId;
    private String nomeProduto;
    private String classeFaturamento;
    private String classeLucratividade;
    // CAMPEAO_DE_VENDAS: A em Faturamento, C em Lucratividade — vende muito,
    //   mas a margem é ruim (ex.: produto de entrada com custo de peça alto).
    // MOTOR_DE_LUCRO: C em Faturamento, A em Lucratividade — vende pouco,
    //   mas cada venda deixa margem alta (ex.: produto premium/nichado).
    // ALINHADO: mesma classe nas duas curvas — sem paradoxo, comportamento
    //   consistente.
    // MISTO: qualquer outra combinação (ex.: A/B, B/C) — diferença existe
    //   mas não é o paradoxo extremo A/C.
    private String quadrante;
}
