package com.smartstock.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

// Usado só pra devolver alertas de vencimento (GET /produtos/alertas-vencimento).
// Lote.produto é @JsonIgnore (evita loop Produto -> lotes -> produto -> lotes...),
// então esse DTO carrega o nome do produto separado, achatado.
@Data
@AllArgsConstructor
public class LoteAlertaDTO {
    private Long produtoId;
    private String produtoNome;
    private String numeroLote;
    private Integer quantidade;
    private LocalDate dataValidade;
}
