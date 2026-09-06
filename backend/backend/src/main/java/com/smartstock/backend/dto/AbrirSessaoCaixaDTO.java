package com.smartstock.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AbrirSessaoCaixaDTO {
    // Fundo de troco declarado (opcional).
    private BigDecimal valorAbertura;
    private String observacao;
}
