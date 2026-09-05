package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Livro-caixa: cada entrada/saída real de dinheiro da empresa. Diferente de
 * Despesa (que é a OBRIGAÇÃO de pagar) e Movimentacao (que é o ESTOQUE saindo),
 * isso aqui é o DINHEIRO em si — o saldo disso é a linha "Disponibilidades"
 * do Ativo Circulante, e o extrato por período é a Demonstração de Fluxo de
 * Caixa (DFC).
 *
 * A grande maioria dos registros nasce sozinha, disparada por outros
 * serviços (venda no PDV que não é fiado, fiado sendo pago, despesa sendo
 * paga) — ver CaixaService.registrarEntrada/registrarSaida e os pontos que
 * chamam esses métodos. Só os lançamentos de APORTE_SOCIO/RETIRADA_SOCIO/
 * OUTRO são digitados manualmente pelo lojista, porque não têm de onde ser
 * derivados automaticamente.
 */
@Data
@Entity
@Table(name = "movimentos_caixa")
public class MovimentoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentoCaixa tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemCaixa origem;

    // Sempre positivo — é o campo "tipo" que diz se soma ou subtrai do saldo.
    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    @Column(name = "data_movimento", nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    // 🆕 Rastreabilidade: mesmo raciocínio de Movimentacao.usuarioId/usuarioNome
    // — snapshot, não @ManyToOne, pra não quebrar se o usuário for excluído.
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_nome", length = 150)
    private String usuarioNome;
}
