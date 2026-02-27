package com.smartstock.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraficoDTO {
    private String data;
    private Integer entradas;
    private Integer saidas;
}