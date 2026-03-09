package com.smartstock.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LoteDTO {
    private String numeroLote;
    private Integer quantidade;
    private LocalDate dataValidade;
}