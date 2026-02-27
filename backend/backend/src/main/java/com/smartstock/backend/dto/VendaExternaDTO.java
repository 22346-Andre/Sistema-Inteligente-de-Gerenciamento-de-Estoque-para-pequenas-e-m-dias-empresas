package com.smartstock.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class VendaExternaDTO {
    private String origem; // Ex: "MERCADO_LIVRE", "SHOPIFY"
    private String idPedido;
    private Long empresaId;
    private List<ItemVendaExternaDTO> itens;
}
