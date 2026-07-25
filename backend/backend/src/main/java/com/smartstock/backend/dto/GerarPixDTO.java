package com.smartstock.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GerarPixDTO(
        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
        BigDecimal valor,

        // Identificador livre (ex.: "VENDA123", "FIADO45") — opcional, aparece
        // como referência da cobrança pro lojista. Se não vier, usa "***".
        String identificador
) {
}
