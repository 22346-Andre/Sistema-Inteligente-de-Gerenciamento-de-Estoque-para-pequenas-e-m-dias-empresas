package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal; // 🟢 Importe o BigDecimal
import java.time.LocalDate;

@Data
public class LoteDTO {
    private String numeroLote;
    private Integer quantidade;
    private LocalDate dataValidade;
    private BigDecimal novoPrecoCompra;
    private String chaveNotaFiscal;
}