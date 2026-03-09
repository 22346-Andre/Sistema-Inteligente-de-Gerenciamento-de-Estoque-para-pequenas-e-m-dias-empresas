package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class EstatisticasDTO {
    private BigDecimal capitalImobilizado;
    private Double giroEstoque;
    private Long totalProdutos;
    private Long produtosCriticos;
    private List<CurvaABCDTO> curvaABC;
}