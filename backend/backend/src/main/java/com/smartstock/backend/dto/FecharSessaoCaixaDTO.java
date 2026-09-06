package com.smartstock.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FecharSessaoCaixaDTO {
    // Quanto o operador contou em dinheiro ao fechar (opcional, conferência manual).
    private BigDecimal valorFechamentoInformado;
    private String observacao;
}
