package com.smartstock.backend.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class VendaExternaDTO {
    @NotBlank
    @Size(max = 64)
    private String origem; // Ex: "MERCADO_LIVRE", "SHOPIFY"

    @NotBlank
    @Size(max = 128)
    private String idPedido;

    @NotEmpty
    private List<@NotNull @Valid ItemVendaExternaDTO> itens;
}
