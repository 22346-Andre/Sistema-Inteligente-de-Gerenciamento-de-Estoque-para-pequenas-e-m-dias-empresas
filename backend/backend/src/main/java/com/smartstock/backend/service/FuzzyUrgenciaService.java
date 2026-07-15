package com.smartstock.backend.service;

import org.springframework.stereotype.Service;

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
     * Avalia a urgência de compra (0 a 100) para um produto, usando as mesmas
     * regras fuzzy que tínhamos no FCL, agora resolvidas em Java puro.
     */
    public double calcularUrgencia(double nivelEstoquePct, double giroVendas, double prazoEntregaDias) {
        double nivel = Math.max(0, nivelEstoquePct);
        double giro = Math.max(0, giroVendas);
        double prazo = Math.max(0, prazoEntregaDias);

        // --- Fuzzificação das entradas ---
        double estBaixo = pertinencia(nivel, ESTOQUE_BAIXO);
        double estAdequado = pertinencia(nivel, ESTOQUE_ADEQUADO);
        double estAlto = pertinencia(nivel, ESTOQUE_ALTO);

        double giroLento = pertinencia(giro, GIRO_LENTO);
        double giroModerado = pertinencia(giro, GIRO_MODERADO);
        double giroRapido = pertinencia(giro, GIRO_RAPIDO);

        double prazoRapido = pertinencia(prazo, PRAZO_RAPIDO);
        double prazoAceitavel = pertinencia(prazo, PRAZO_ACEITAVEL);
        double prazoDemorado = pertinencia(prazo, PRAZO_DEMORADO);

        // --- Avaliação das regras (AND = MIN) ---
        double r1 = Math.min(estBaixo, Math.min(giroRapido, prazoDemorado));   // -> alta
        double r2 = Math.min(estBaixo, Math.min(giroRapido, prazoAceitavel));  // -> alta
        double r3 = Math.min(estBaixo, giroModerado);                          // -> media
        double r4 = Math.min(estBaixo, giroLento);                             // -> baixa
        double r5 = Math.min(estAdequado, giroRapido);                         // -> media
        double r6 = Math.min(estAdequado, giroModerado);                       // -> baixa
        double r7 = Math.min(estAdequado, giroLento);                          // -> nula
        double r8 = estAlto;                                                   // -> nula
        double r9 = Math.min(estBaixo, prazoDemorado);                         // -> alta

        // --- Agregação por termo de saída (ACCU = MAX) ---
        double forcaAlta  = Math.max(r1, Math.max(r2, r9));
        double forcaMedia = Math.max(r3, r5);
        double forcaBaixa = Math.max(r4, r6);
        double forcaNula  = Math.max(r7, r8);

        // Se nenhuma regra disparou, urgência é 0 (equivalente ao DEFAULT do FCL)
        if (forcaAlta == 0 && forcaMedia == 0 && forcaBaixa == 0 && forcaNula == 0) {
            return 0.0;
        }

        // --- Defuzzificação por Centro de Gravidade (COG), discretizado ---
        double somaXMu = 0.0;
        double somaMu = 0.0;

        for (double x = UNIVERSO_MIN; x <= UNIVERSO_MAX; x += PASSO) {
            double muAlta  = Math.min(forcaAlta,  pertinencia(x, URGENCIA_ALTA));
            double muMedia = Math.min(forcaMedia, pertinencia(x, URGENCIA_MEDIA));
            double muBaixa = Math.min(forcaBaixa, pertinencia(x, URGENCIA_BAIXA));
            double muNula  = Math.min(forcaNula,  pertinencia(x, URGENCIA_NULA));

            double mu = Math.max(Math.max(muAlta, muMedia), Math.max(muBaixa, muNula));

            somaXMu += x * mu;
            somaMu += mu;
        }

        if (somaMu == 0) return 0.0;

        return somaXMu / somaMu;
    }
}