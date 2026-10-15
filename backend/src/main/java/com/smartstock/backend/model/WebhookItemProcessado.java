package com.smartstock.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "webhook_itens_processados",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_webhook_item_pedido",
                columnNames = {"empresa_id", "origem", "pedido_id", "codigo_barras"}
        )
)
public class WebhookItemProcessado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false, length = 64)
    private String origem;

    @Column(name = "pedido_id", nullable = false, length = 128)
    private String pedidoId;

    @Column(name = "codigo_barras", nullable = false, length = 100)
    private String codigoBarras;

    @Column(name = "quantidade", nullable = false)
    private Integer quantidade;

    @Column(name = "chave_movimentacao", nullable = false, length = 44)
    private String chaveMovimentacao;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;
}
