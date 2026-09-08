package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  alerta interno pro gestor, exibido no sistema (não é e-mail).
 * Criado, por enquanto, especificamente para falhas parciais de Webhook —
 * "a venda X do canal Y teve um item que não pôde ser processado" — mas
 * desenhado de forma genérica (tipo + titulo + mensagem) pra poder ser
 * reaproveitado por outros alertas internos no futuro sem precisar de nova
 * tabela.
 */
@Entity
@Table(name = "notificacoes")
@Data
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // Ex.: "WEBHOOK_FALHA_PARCIAL" — permite o frontend escolher ícone/cor
    // por tipo, e permite filtrar/agrupar no futuro sem parsear texto livre.
    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(nullable = false)
    private boolean lida = false;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @PrePersist
    protected void aoPersistir() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }
}
