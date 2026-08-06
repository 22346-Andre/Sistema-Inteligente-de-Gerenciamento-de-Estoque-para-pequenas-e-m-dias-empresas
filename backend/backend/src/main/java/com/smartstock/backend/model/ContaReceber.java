package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contas_receber")
public class ContaReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false)
    private String nomeCliente;

    @Column(nullable = false)
    private String telefoneCliente;

    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    private LocalDateTime dataCompra = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDate dataVencimento;

    @Column(nullable = false)
    private LocalDate dataProximaCobranca;

    @Enumerated(EnumType.STRING)
    private StatusConta status = StatusConta.PENDENTE;

    //  Preenchido quando uma cobrança Pix dinâmica é gerada via Delfinance.
    // É esse identificador que volta no webhook PIX_RECEIVED e permite
    // marcar a conta como paga automaticamente, sem o lojista precisar
    // confirmar manualmente.
    @Column(name = "pix_correlation_id", length = 60)
    private String pixCorrelationId;
}
