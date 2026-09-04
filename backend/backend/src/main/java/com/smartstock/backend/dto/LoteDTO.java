package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal; 
import java.time.LocalDate;

@Data
public class LoteDTO {
    private String numeroLote;
    private Integer quantidade;
    private LocalDate dataValidade;
    private BigDecimal novoPrecoCompra;
    private String chaveNotaFiscal;

    // 🆕 Efeito no Caixa/Contas a Pagar. Só faz diferença quando
    // novoPrecoCompra > 0 (senão não há valor de compra pra lançar em
    // nenhum lugar).
    // true  -> baixa o valor direto do Caixa agora (compra à vista).
    // false/null -> cria uma Despesa PENDENTE (fornecedor), que só bate no
    //               Caixa quando for marcada como paga.
    private Boolean pagamentoImediato;

    // Usados só quando pagamentoImediato = false. fornecedorId é opcional
    // (cai pro fornecedor já cadastrado no produto, se houver); dataVencimento
    // usa hoje+30 dias se não vier preenchida.
    private Long fornecedorId;
    private LocalDate dataVencimento;
    private String categoria;
}