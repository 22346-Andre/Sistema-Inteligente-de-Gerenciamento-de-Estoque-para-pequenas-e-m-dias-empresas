package com.smartstock.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Motor fuzzy de urgência: base completa de 27 regras (3 x 3 x 3), sem combinação de entradas
 * descoberta, e comportamento esperado nos casos de negócio mais importantes.
 */
class FuzzyUrgenciaServiceTest {

    private final FuzzyUrgenciaService fuzzy = new FuzzyUrgenciaService();

    private static final double LIMIAR_URGENTE = 55.0;

    @Test
    void baseTemUmaRegraParaCadaUmaDasVinteESeteCombinacoes() {
        Set<String> combinacoes = new HashSet<>();
        for (FuzzyUrgenciaService.Regra r : FuzzyUrgenciaService.BASE_DE_REGRAS) {
            combinacoes.add(r.estoque() + "|" + r.giro() + "|" + r.prazo());
        }

        assertEquals(27, FuzzyUrgenciaService.BASE_DE_REGRAS.size());
        assertEquals(27, combinacoes.size()); // nenhuma combinação repetida

        for (FuzzyUrgenciaService.Estoque e : FuzzyUrgenciaService.Estoque.values()) {
            for (FuzzyUrgenciaService.Giro g : FuzzyUrgenciaService.Giro.values()) {
                for (FuzzyUrgenciaService.Prazo p : FuzzyUrgenciaService.Prazo.values()) {
                    assertTrue(combinacoes.contains(e + "|" + g + "|" + p),
                            "Sem regra para " + e + " / " + g + " / " + p);
                }
            }
        }
    }

    @Test
    void nenhumaEntradaDaGradeFicaSemUrgencia() {
        for (int estoque = 0; estoque <= 300; estoque += 10) {
            for (int giro = 0; giro <= 200; giro += 10) {
                for (int prazo = 0; prazo <= 30; prazo++) {
                    double u = fuzzy.calcularUrgencia(estoque, giro, prazo);
                    assertTrue(u > 0 && u <= 100,
                            "Urgência fora do esperado (" + u + ") para estoque=" + estoque
                                    + ", giro=" + giro + ", prazo=" + prazo);
                }
            }
        }
    }

    @Test
    void exemploComEstoqueBaixoEGiroModeradoRapido() {
        assertEquals(72.3, fuzzy.calcularUrgencia(40, 70, 10), 0.1);
    }

    @Test
    void exemploComEstoqueProximoDoMinimoEGiroLento() {
        assertEquals(31.7, fuzzy.calcularUrgencia(60, 20, 3), 0.1);
    }

    @Test
    void estoqueBaixoComGiroRapidoEEntregaRapidaEUrgente() {
        // Combinação que antes não tinha regra (urgência 0).
        for (int prazo = 0; prazo <= 7; prazo++) {
            assertTrue(fuzzy.calcularUrgencia(20, 100, prazo) >= LIMIAR_URGENTE,
                    "Esperava URGENTE com prazo de " + prazo + " dia(s)");
        }
        assertTrue(fuzzy.calcularUrgencia(0, 100, 2) >= LIMIAR_URGENTE);
    }

    @Test
    void estoqueBaixoComEntregaDemoradaESempreUrgenteQualquerQueSejaOGiro() {
        for (int giro = 0; giro <= 200; giro += 20) {
            assertTrue(fuzzy.calcularUrgencia(20, giro, 25) >= LIMIAR_URGENTE,
                    "Esperava URGENTE com giro=" + giro);
        }
    }

    @Test
    void estoqueAltoNuncaGeraUrgencia() {
        for (int giro = 0; giro <= 200; giro += 20) {
            for (int prazo = 0; prazo <= 30; prazo += 5) {
                assertTrue(fuzzy.calcularUrgencia(250, giro, prazo) < 30,
                        "Estoque alto não deveria gerar urgência (giro=" + giro + ", prazo=" + prazo + ")");
            }
        }
    }

    @Test
    void urgenciaNaoDiminuiQuandoOEstoqueCaiParaOMesmoGiroEPrazo() {
        assertTrue(fuzzy.calcularUrgencia(20, 70, 10) >= fuzzy.calcularUrgencia(120, 70, 10));
        assertTrue(fuzzy.calcularUrgencia(120, 70, 10) >= fuzzy.calcularUrgencia(250, 70, 10));
    }
}