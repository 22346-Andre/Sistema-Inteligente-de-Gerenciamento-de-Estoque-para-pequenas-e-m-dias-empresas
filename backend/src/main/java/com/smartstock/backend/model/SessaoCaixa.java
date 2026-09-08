package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Turno de caixa: cada operador abre a própria sessão no início do dia/turno
 * e fecha no fim, pra ter controle de quando entrou/saiu de cada caixa —
 * "Caixa 1 = Fulano das 08h às 14h", "Caixa 2 = Sicrano das 14h às 20h".
 *
 * Uma pessoa só pode ter UMA sessão aberta (dataFechamento == null) por vez
 * — é a trava que evita abrir duas sem fechar a anterior. Não impede duas
 * PESSOAS diferentes com sessão aberta ao mesmo tempo (é esperado, cada
 * caixa físico/operador tem a sua).
 *
 * v1: só registra quem/quando abriu e fechou, e os valores informados pelo
 * operador (não confere automaticamente contra as vendas do período) — é
 * um registro de controle/rastreabilidade, não uma conciliação de caixa.
 */
@Data
@Entity
@Table(name = "sessoes_caixa")
public class SessaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "usuario_abertura_id", nullable = false)
    private Long usuarioAberturaId;

    @Column(name = "usuario_abertura_nome", length = 150, nullable = false)
    private String usuarioAberturaNome;

    @Column(name = "data_abertura", nullable = false)
    private LocalDateTime dataAbertura = LocalDateTime.now();

    // Fundo de troco declarado ao abrir (opcional).
    @Column(name = "valor_abertura")
    private BigDecimal valorAbertura;

    // NULL enquanto a sessão está aberta.
    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    @Column(name = "usuario_fechamento_id")
    private Long usuarioFechamentoId;

    @Column(name = "usuario_fechamento_nome", length = 150)
    private String usuarioFechamentoNome;

    // Quanto o operador contou em dinheiro ao fechar (opcional, pra conferência manual).
    @Column(name = "valor_fechamento_informado")
    private BigDecimal valorFechamentoInformado;

    // 🆕 Calculado no fechamento: fundo de troco + vendas em ESPÉCIE do
    // turno. Guardado (não recalculado depois), pra o histórico não mudar
    // se um lançamento de caixa antigo for editado/cancelado.
    @Column(name = "valor_esperado")
    private BigDecimal valorEsperado;

    @Column(length = 255)
    private String observacao;
}
