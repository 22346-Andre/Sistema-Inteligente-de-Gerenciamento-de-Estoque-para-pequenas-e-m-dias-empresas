package com.smartstock.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Botão de pânico" do estoque zerado: só dispara com os três freios de segurança satisfeitos. */
class RegraRupturaEstoqueTest {

    @Test
    void disparaParaItemZeradoComMinimoEClasseAOuB() {
        assertTrue(RegraRupturaEstoque.aplica(0, 10, "A"));
        assertTrue(RegraRupturaEstoque.aplica(0, 10, "B"));
    }

    @Test
    void naoDisparaParaItemDeClasseCOuSemClasse() {
        assertFalse(RegraRupturaEstoque.aplica(0, 10, "C"));
        assertFalse(RegraRupturaEstoque.aplica(0, 10, null));
    }

    @Test
    void naoDisparaQuandoALojaAceitaFicarSemOItem() {
        assertFalse(RegraRupturaEstoque.aplica(0, 0, "A"));
    }

    @Test
    void naoDisparaQuandoAindaHaEstoque() {
        assertFalse(RegraRupturaEstoque.aplica(3, 10, "A"));
    }

    @Test
    void estoqueNegativoContaComoZerado() {
        assertTrue(RegraRupturaEstoque.aplica(-2, 10, "A"));
    }

    @Test
    void pisoSobeUrgenciaBaixaMasNaoDerrubaUrgenciaAlta() {
        assertEquals(85.0, RegraRupturaEstoque.aplicarPiso(30.0, true));
        assertEquals(92.0, RegraRupturaEstoque.aplicarPiso(92.0, true));
    }

    @Test
    void semDisparoOGrauFuzzyFicaIntacto() {
        assertEquals(30.0, RegraRupturaEstoque.aplicarPiso(30.0, false));
    }
}
