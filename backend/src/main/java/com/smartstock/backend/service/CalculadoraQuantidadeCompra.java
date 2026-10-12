package com.smartstock.backend.service;

import java.util.Locale;

/**
 * Quanto o gestor deve comprar de um produto.
 *
 * Duas bases, vale a maior (a sugestão nunca fica abaixo do que o sistema já sugeria):
 *  - base por estoque mínimo: repor até 150% do mínimo (comportamento anterior);
 *  - base por demanda: cobre as vendas durante o prazo de entrega, mais
 *    {@value #DIAS_COBERTURA_APOS_CHEGADA} dias de venda depois que o pedido chega, mais o
 *    estoque mínimo como reserva de segurança. Só vale se houve venda nos últimos 30 dias.
 *
 * Em cima da base ainda entra o ajuste de urgência anterior (até +30% do mínimo).
 * O texto de motivo é mostrado ao gestor, para ele entender de onde veio o número.
 *
 * Classe pura, sem Spring, para ser testável isoladamente.
 */
public final class CalculadoraQuantidadeCompra {

    public static final int DIAS_COBERTURA_APOS_CHEGADA = 15;
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private CalculadoraQuantidadeCompra() {
    }

    public record Resultado(int quantidade, double consumoDiarioMedio, String motivo) {
    }

    public static Resultado calcular(int atual, int minimo, int saidasUltimos30Dias,
                                     double prazoEntregaDias, double grauUrgencia) {
        int estoque = Math.max(atual, 0);
        int minimoSeguro = Math.max(minimo, 0);
        int vendas = Math.max(saidasUltimos30Dias, 0);
        double prazo = Math.max(prazoEntregaDias, 0);

        double consumoDiario = vendas / 30.0;

        int baseMinimo = Math.max((int) Math.ceil(minimoSeguro * 1.5) - estoque, 1);

        int baseDemanda = 0;
        if (consumoDiario > 0) {
            int alvoDemanda = (int) Math.ceil(consumoDiario * (prazo + DIAS_COBERTURA_APOS_CHEGADA)) + minimoSeguro;
            baseDemanda = Math.max(alvoDemanda - estoque, 1);
        }

        int base = Math.max(baseMinimo, baseDemanda);
        int ajusteUrgencia = (int) Math.round((grauUrgencia / 100.0) * minimoSeguro * 0.3);
        int quantidade = base + ajusteUrgencia;

        String motivo;
        if (baseDemanda > baseMinimo) {
            motivo = String.format(PT_BR,
                    "Cobre %.0f dias de entrega + %d dias de vendas (média de %.1f un./dia) + estoque mínimo de reserva.",
                    prazo, DIAS_COBERTURA_APOS_CHEGADA, consumoDiario);
        } else if (consumoDiario > 0) {
            motivo = "Repõe até 150% do estoque mínimo; o ritmo de vendas atual não exige mais que isso.";
        } else {
            motivo = "Sem vendas nos últimos 30 dias: repõe até 150% do estoque mínimo.";
        }
        if (ajusteUrgencia > 0) {
            motivo += " Inclui +" + ajusteUrgencia + " un. pela urgência.";
        }

        return new Resultado(quantidade, consumoDiario, motivo);
    }
}
