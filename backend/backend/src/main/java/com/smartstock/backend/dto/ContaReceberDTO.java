package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContaReceberDTO {
    private String nomeCliente;
    private String telefoneCliente;
    private BigDecimal valor;
    private String descricao;
    private LocalDate dataVencimento;


    private LocalDate dataProximaCobranca;
}