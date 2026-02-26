package com.smartstock.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LoteDTO {
    private Integer quantidade;
    private LocalDate dataValidade;
}