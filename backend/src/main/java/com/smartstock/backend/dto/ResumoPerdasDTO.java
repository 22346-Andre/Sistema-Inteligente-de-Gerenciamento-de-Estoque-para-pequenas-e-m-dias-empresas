package com.smartstock.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumoPerdasDTO {
    private BigDecimal total;
    private List<PerdasPorMesDTO> porMes;
}
