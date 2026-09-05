package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "movimentacoes")
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne // Relacionamento com a tabela de Produtos
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING) // Salva como texto ("ENTRADA" ou "SAIDA") no banco
    @Column(nullable = false)
    private TipoMovimentacao tipo;

    @Column(nullable = false)
    private Integer quantidade;

    private LocalDateTime dataMovimentacao = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(length = 255)
    private String motivo;

    @Column(length = 44)
    private String chaveNotaFiscal;

    //  Forma de pagamento da venda (só preenchido em SAIDA feita pelo PDV).
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    // 🆕 Rastreabilidade: quem fez essa movimentação e (via dataMovimentacao,
    // que já existia) a que horas. Guardado como snapshot (id + nome), não
    // como @ManyToOne pro Usuario, porque usuário pode ser excluído depois —
    // o histórico não pode quebrar nem virar "usuário null" quando isso
    // acontecer.
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_nome", length = 150)
    private String usuarioNome;
}