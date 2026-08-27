package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DespesaDTO {
    private String descricao;
    private String categoria;
    private BigDecimal valor;
    private LocalDate dataVencimento;
    private Long fornecedorId; // opcional
}
