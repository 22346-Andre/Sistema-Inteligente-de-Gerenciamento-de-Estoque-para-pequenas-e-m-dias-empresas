package com.smartstock.backend.dto;

import com.smartstock.backend.model.FormaPagamento;
import com.smartstock.backend.model.TipoMovimentacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SaidaDTO {
    @NotNull
    @Positive
    private Integer quantidadeDesejada;


    private TipoMovimentacao tipo;
    private String motivo;
    private String chaveNotaFiscal;
    private FormaPagamento formaPagamento; //  forma de pagamento escolhida no PDV
}