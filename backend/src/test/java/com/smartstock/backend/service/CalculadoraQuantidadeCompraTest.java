package com.smartstock.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quantidade sugerida de compra: maior entre "repor o mínimo" e "cobrir a demanda". */
class CalculadoraQuantidadeCompraTest {

    @Test
    void semVendasRepoeAte150PorCentoDoMinimoMaisAjusteDeUrgencia() {
        // ceil(10 * 1.5) - 0 = 15 ; ajuste = round(0,30 * 10 * 0,3) = 1
        var r = CalculadoraQuantidadeCompra.calcular(0, 10, 0, 7, 30);
        assertEquals(16, r.quantidade());
        assertTrue(r.motivo().startsWith("Sem vendas"));
    }

    @Test
    void comGiroAltoADemandaManda() {
        // 90 vendas/30 dias = 3/dia ; 3 * (7 + 15) = 66 ; + mínimo 10 = 76 ; ajuste = round(0,85 * 10 * 0,3) = 3
        var r = CalculadoraQuantidadeCompra.calcular(0, 10, 90, 7, 85);
        assertEquals(79, r.quantidade());
        assertEquals(3.0, r.consumoDiarioMedio());
        assertTrue(r.motivo().contains("3,0 un./dia"));
        assertTrue(r.motivo().contains("7 dias de entrega"));
    }

    @Test
    void quandoOMinimoEMaiorQueADemandaOMinimoPrevalece() {
        // demanda: 0,33 * 22 = 8 + 100 = 108 ; base do mínimo: 150
        var r = CalculadoraQuantidadeCompra.calcular(0, 100, 10, 7, 0);
        assertEquals(150, r.quantidade());
    }

    @Test
    void nuncaSugereMenosQueUmaUnidade() {
        assertEquals(1, CalculadoraQuantidadeCompra.calcular(50, 10, 15, 7, 10).quantidade());
        assertEquals(1, CalculadoraQuantidadeCompra.calcular(0, 0, 0, 7, 0).quantidade());
    }
}
