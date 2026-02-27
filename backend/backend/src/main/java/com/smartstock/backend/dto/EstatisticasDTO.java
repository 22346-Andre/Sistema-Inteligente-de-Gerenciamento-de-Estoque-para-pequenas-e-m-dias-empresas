package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class EstatisticasDTO {
    private BigDecimal capitalImobilizado;
    private Double giroEstoque30Dias; // Taxa de giro do mês
    private List<CurvaABCDTO> curvaABC;
}