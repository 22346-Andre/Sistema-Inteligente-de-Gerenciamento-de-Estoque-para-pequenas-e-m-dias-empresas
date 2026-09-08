package com.smartstock.backend.dto;

import com.smartstock.backend.model.OrigemCaixa;
import com.smartstock.backend.model.TipoMovimentoCaixa;
import lombok.Data;
import java.math.BigDecimal;

// Usado só pra lançamento manual (aporte/retirada de sócio, ou "outro") —
// os lançamentos automáticos (venda, fiado pago, despesa paga) não passam
// por esse DTO, são criados direto pelo service que originou o evento.
@Data
public class LancamentoCaixaDTO {
    private TipoMovimentoCaixa tipo;
    private OrigemCaixa origem;
    private BigDecimal valor;
    private String descricao;
}
