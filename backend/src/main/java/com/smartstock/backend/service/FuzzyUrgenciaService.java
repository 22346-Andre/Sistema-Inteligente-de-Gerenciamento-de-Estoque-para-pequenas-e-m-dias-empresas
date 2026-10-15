package com.smartstock.backend.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FuzzyUrgenciaService {

    // ---------------------------------------------------------
    // Funções de pertinência (pontos: {x, grau_pertinencia})
    // Mesmos valores que definimos nas regras FCL originais.
    // ---------------------------------------------------------

    // nivel_estoque (% do estoque mínimo)
    private static final double[][] ESTOQUE_BAIXO     = {{0, 1}, {50, 1}, {100, 0}};
    private static final double[][] ESTOQUE_ADEQUADO   = {{80, 0}, {100, 1}, {150, 1}, {200, 0}};
    private static final double[][] ESTOQUE_ALTO       = {{150, 0}, {200, 1}, {300, 1}};

    // giro_vendas (unidades vendidas em 30 dias)
    private static final double[][] GIRO_LENTO         = {{0, 1}, {20, 1}, {50, 0}};
    private static final double[][] GIRO_MODERADO      = {{30, 0}, {50, 1}, {80, 0}};
    private static final double[][] GIRO_RAPIDO        = {{60, 0}, {80, 1}, {200, 1}};

    // prazo_entrega (dias)
    private static final double[][] PRAZO_RAPIDO       = {{0, 1}, {3, 1}, {7, 0}};
    private static final double[][] PRAZO_ACEITAVEL    = {{5, 0}, {10, 1}, {15, 0}};
    private static final double[][] PRAZO_DEMORADO     = {{12, 0}, {20, 1}, {30, 1}};

    // urgencia (saída, universo de 0 a 100)
    private static final double[][] URGENCIA_NULA      = {{0, 1}, {15, 1}, {30, 0}};
    private static final double[][] URGENCIA_BAIXA      = {{15, 0}, {30, 1}, {50, 0}};
    private static final double[][] URGENCIA_MEDIA      = {{35, 0}, {55, 1}, {75, 0}};
    private static final double[][] URGENCIA_ALTA       = {{60, 0}, {80, 1}, {100, 1}};

    private static final double UNIVERSO_MIN = 0;
    private static final double UNIVERSO_MAX = 100;
    private static final double PASSO = 0.5; // resolução da discretização para o COG

    // ---------------------------------------------------------
    // Termos linguísticos e base de regras
    // ---------------------------------------------------------

    enum Estoque { BAIXO, ADEQUADO, ALTO }
    enum Giro { LENTO, MODERADO, RAPIDO }
    enum Prazo { RAPIDO, ACEITAVEL, DEMORADO }
    enum Urgencia { NULA, BAIXA, MEDIA, ALTA }

    /** SE estoque É x E giro É y E prazo É z ENTÃO urgência É w. */
    record Regra(Estoque estoque, Giro giro, Prazo prazo, Urgencia urgencia) { }

    /**
     * Base de regras COMPLETA: 3 termos de estoque x 3 de giro x 3 de prazo = 27 regras,
     * uma para cada combinação possível das entradas (nenhuma combinação fica sem regra).
     *
     * Critérios usados para definir a urgência de cada combinação:
     *  - Estoque BAIXO (abaixo do mínimo): a urgência cresce com o giro (quanto mais vende,
     *    mais rápido falta) e com o prazo (entrega demorada exige antecedência).
     *    Prazo DEMORADO com estoque baixo é sempre ALTA, qualquer que seja o giro.
     *    Giro RÁPIDO com estoque baixo é sempre ALTA, mesmo com entrega rápida: o produto
     *    pode acabar antes de o giro ser percebido.
     *  - Estoque ADEQUADO (próximo ao mínimo, até 200%): o colchão de estoque absorve a
     *    entrega, então só o giro decide (rápido = MÉDIA, moderado = BAIXA, lento = NULA).
     *  - Estoque ALTO (acima de 150-200% do mínimo): NULA, qualquer que seja o giro e o prazo.
     *
     *                          prazo RÁPIDO   prazo ACEITÁVEL   prazo DEMORADO
     *   BAIXO    giro LENTO       BAIXA          BAIXA            ALTA
     *            giro MODERADO    MÉDIA          MÉDIA            ALTA
     *            giro RÁPIDO      ALTA           ALTA             ALTA
     *   ADEQUADO giro LENTO       NULA           NULA             NULA
     *            giro MODERADO    BAIXA          BAIXA            BAIXA
     *            giro RÁPIDO      MÉDIA          MÉDIA            MÉDIA
     *   ALTO     (qualquer giro)  NULA           NULA             NULA
     */
    static final List<Regra> BASE_DE_REGRAS = List.of(
            // ---- Estoque BAIXO ----
            new Regra(Estoque.BAIXO, Giro.LENTO,    Prazo.RAPIDO,    Urgencia.BAIXA),  // R01
            new Regra(Estoque.BAIXO, Giro.LENTO,    Prazo.ACEITAVEL, Urgencia.BAIXA),  // R02
            new Regra(Estoque.BAIXO, Giro.LENTO,    Prazo.DEMORADO,  Urgencia.ALTA),   // R03
            new Regra(Estoque.BAIXO, Giro.MODERADO, Prazo.RAPIDO,    Urgencia.MEDIA),  // R04
            new Regra(Estoque.BAIXO, Giro.MODERADO, Prazo.ACEITAVEL, Urgencia.MEDIA),  // R05
            new Regra(Estoque.BAIXO, Giro.MODERADO, Prazo.DEMORADO,  Urgencia.ALTA),   // R06
            new Regra(Estoque.BAIXO, Giro.RAPIDO,   Prazo.RAPIDO,    Urgencia.ALTA),   // R07
            new Regra(Estoque.BAIXO, Giro.RAPIDO,   Prazo.ACEITAVEL, Urgencia.ALTA),   // R08
            new Regra(Estoque.BAIXO, Giro.RAPIDO,   Prazo.DEMORADO,  Urgencia.ALTA),   // R09
            // ---- Estoque ADEQUADO ----
            new Regra(Estoque.ADEQUADO, Giro.LENTO,    Prazo.RAPIDO,    Urgencia.NULA),   // R10
            new Regra(Estoque.ADEQUADO, Giro.LENTO,    Prazo.ACEITAVEL, Urgencia.NULA),   // R11
            new Regra(Estoque.ADEQUADO, Giro.LENTO,    Prazo.DEMORADO,  Urgencia.NULA),   // R12
            new Regra(Estoque.ADEQUADO, Giro.MODERADO, Prazo.RAPIDO,    Urgencia.BAIXA),  // R13
            new Regra(Estoque.ADEQUADO, Giro.MODERADO, Prazo.ACEITAVEL, Urgencia.BAIXA),  // R14
            new Regra(Estoque.ADEQUADO, Giro.MODERADO, Prazo.DEMORADO,  Urgencia.BAIXA),  // R15
            new Regra(Estoque.ADEQUADO, Giro.RAPIDO,   Prazo.RAPIDO,    Urgencia.MEDIA),  // R16
            new Regra(Estoque.ADEQUADO, Giro.RAPIDO,   Prazo.ACEITAVEL, Urgencia.MEDIA),  // R17
            new Regra(Estoque.ADEQUADO, Giro.RAPIDO,   Prazo.DEMORADO,  Urgencia.MEDIA),  // R18
            // ---- Estoque ALTO ----
            new Regra(Estoque.ALTO, Giro.LENTO,    Prazo.RAPIDO,    Urgencia.NULA),  // R19
            new Regra(Estoque.ALTO, Giro.LENTO,    Prazo.ACEITAVEL, Urgencia.NULA),  // R20
            new Regra(Estoque.ALTO, Giro.LENTO,    Prazo.DEMORADO,  Urgencia.NULA),  // R21
            new Regra(Estoque.ALTO, Giro.MODERADO, Prazo.RAPIDO,    Urgencia.NULA),  // R22
            new Regra(Estoque.ALTO, Giro.MODERADO, Prazo.ACEITAVEL, Urgencia.NULA),  // R23
            new Regra(Estoque.ALTO, Giro.MODERADO, Prazo.DEMORADO,  Urgencia.NULA),  // R24
            new Regra(Estoque.ALTO, Giro.RAPIDO,   Prazo.RAPIDO,    Urgencia.NULA),  // R25
            new Regra(Estoque.ALTO, Giro.RAPIDO,   Prazo.ACEITAVEL, Urgencia.NULA),  // R26
            new Regra(Estoque.ALTO, Giro.RAPIDO,   Prazo.DEMORADO,  Urgencia.NULA)   // R27
    );

    /** Funções de pertinência da saída, na ordem do enum Urgencia (NULA, BAIXA, MEDIA, ALTA). */
    private static final double[][][] URGENCIA_FUNCOES = {
            URGENCIA_NULA, URGENCIA_BAIXA, URGENCIA_MEDIA, URGENCIA_ALTA
    };

    /**
     * Calcula o grau de pertinência (0 a 1) de x numa função linear por partes.
     * Fora do intervalo definido, assume o valor da extremidade mais próxima.
     */
    private double pertinencia(double x, double[][] pontos) {
        int n = pontos.length;
        if (x <= pontos[0][0]) return pontos[0][1];
        if (x >= pontos[n - 1][0]) return pontos[n - 1][1];

        for (int i = 0; i < n - 1; i++) {
            double x1 = pontos[i][0], y1 = pontos[i][1];
            double x2 = pontos[i + 1][0], y2 = pontos[i + 1][1];
            if (x >= x1 && x <= x2) {
                if (x2 == x1) return y1;
                double t = (x - x1) / (x2 - x1);
                return y1 + t * (y2 - y1);
            }
        }
        return 0.0;
    }

    /**
     * Avalia a urgência de compra (0 a 100) para um produto, usando a base completa de
     * 27 regras (ver BASE_DE_REGRAS), inferência de Mamdani (AND = mínimo, agregação = máximo)
     * e defuzzificação por centro de gravidade.
     */
    public double calcularUrgencia(double nivelEstoquePct, double giroVendas, double prazoEntregaDias) {
        double nivel = Math.max(0, nivelEstoquePct);
        double giro = Math.max(0, giroVendas);
        double prazo = Math.max(0, prazoEntregaDias);

        // --- Fuzzificação das entradas (índices na ordem dos enums) ---
        double[] mEstoque = {
                pertinencia(nivel, ESTOQUE_BAIXO),
                pertinencia(nivel, ESTOQUE_ADEQUADO),
                pertinencia(nivel, ESTOQUE_ALTO)
        };
        double[] mGiro = {
                pertinencia(giro, GIRO_LENTO),
                pertinencia(giro, GIRO_MODERADO),
                pertinencia(giro, GIRO_RAPIDO)
        };
        double[] mPrazo = {
                pertinencia(prazo, PRAZO_RAPIDO),
                pertinencia(prazo, PRAZO_ACEITAVEL),
                pertinencia(prazo, PRAZO_DEMORADO)
        };

        // --- Avaliação das 27 regras (AND = MIN) e agregação por termo de saída (ACCU = MAX) ---
        double[] forca = new double[Urgencia.values().length];
        for (Regra r : BASE_DE_REGRAS) {
            double f = Math.min(mEstoque[r.estoque().ordinal()],
                    Math.min(mGiro[r.giro().ordinal()], mPrazo[r.prazo().ordinal()]));
            int saida = r.urgencia().ordinal();
            forca[saida] = Math.max(forca[saida], f);
        }

        // Com a base completa sempre dispara ao menos uma regra; a checagem abaixo é só uma salvaguarda.
        double forcaTotal = 0;
        for (double f : forca) forcaTotal = Math.max(forcaTotal, f);
        if (forcaTotal == 0) {
            return 0.0;
        }

        // --- Defuzzificação por Centro de Gravidade (COG), discretizado ---
        double somaXMu = 0.0;
        double somaMu = 0.0;

        for (double x = UNIVERSO_MIN; x <= UNIVERSO_MAX; x += PASSO) {
            double mu = 0.0;
            for (int i = 0; i < forca.length; i++) {
                mu = Math.max(mu, Math.min(forca[i], pertinencia(x, URGENCIA_FUNCOES[i])));
            }
            somaXMu += x * mu;
            somaMu += mu;
        }

        if (somaMu == 0) return 0.0;

        return somaXMu / somaMu;
    }
}