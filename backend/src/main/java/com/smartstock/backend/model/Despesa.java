package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Despesa / Conta a Pagar — o lado que faltava pro DRE ter despesas de
 * verdade (não só CMV) e pro Balanço Patrimonial ter Passivo Circulante.
 * Mesmo padrão de ContaReceber (Fiado): status PENDENTE até ser paga,
 * dataPagamento fica nula até lá.
 */
@Data
@Entity
@Table(name = "despesas")
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false)
    private String descricao;

    // Categoria livre (ALUGUEL, SALARIOS, ENERGIA, AGUA, FORNECEDOR,
    // IMPOSTOS, MANUTENCAO, OUTROS...) — string, não enum, pra não travar o
    // lojista numa lista fixa; o frontend sugere as mais comuns num select
    // com opção de digitar outra.
    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate dataVencimento;

    // Nula enquanto a despesa está em aberto (= Contas a Pagar / Passivo
    // Circulante). Preenchida quando o lojista marca como paga.
    private LocalDate dataPagamento;

    // Opcional — quando a despesa é uma compra de um fornecedor já
    // cadastrado, em vez de uma despesa genérica (aluguel, energia etc.).
    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Enumerated(EnumType.STRING)
    private StatusConta status = StatusConta.PENDENTE;

    private LocalDateTime dataCriacao = LocalDateTime.now();
}
