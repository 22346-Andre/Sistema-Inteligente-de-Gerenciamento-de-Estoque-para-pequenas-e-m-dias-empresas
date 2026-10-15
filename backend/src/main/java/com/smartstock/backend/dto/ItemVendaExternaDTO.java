package com.smartstock.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class ItemVendaExternaDTO {

    @NotBlank
    @Size(max = 100)
    private String codigoBarras;

    @NotNull
    @Positive
    private Integer quantidade;
}