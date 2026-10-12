package com.smartstock.backend.service;

/**
 * "Botão de pânico" da sugestão de compra: estoque zerado vira urgência alta,
 * mas SÓ quando os freios de segurança abaixo permitem.
 *
 * Por que existe: sem produto na prateleira não há venda, e sem venda o giro dos
 * últimos 30 dias cai para "Lento" — o fuzzy então baixaria a urgência de um item
 * que na verdade está parado justamente por falta de estoque.
 *
 * Por que tem freios: tratar todo "acabou" como urgente faria o sistema comprar
 * item que saiu de linha ou sazonal fora de época, e gastaria a atenção do gestor
 * com itens de pouca importância.
 *
 * Freios (todos precisam ser verdadeiros):
 *  1. estoque atual zerado;
 *  2. estoque mínimo maior que zero (mínimo 0 = a loja aceita trabalhar sem o item);
 *  3. produto de classe A ou B na curva ABC de faturamento recente (os que trazem
 *     dinheiro — um item sem venda relevante no período não entra em pânico).
 *
 * Fora dessas condições, quem decide é o motor fuzzy.
 *
 * Classe pura, sem Spring, para ser testável isoladamente.
 */
public final class RegraRupturaEstoque {

    /** Piso da urgência quando a regra dispara (escala 0 a 100 do fuzzy). */
    public static final double URGENCIA_MINIMA = 85.0;

    private RegraRupturaEstoque() {
    }

    public static boolean aplica(int quantidadeAtual, int estoqueMinimo, String classeAbc) {
        boolean zerado = quantidadeAtual <= 0;
        boolean aceitaFicarSemEstoque = estoqueMinimo <= 0;
        boolean itemImportante = "A".equals(classeAbc) || "B".equals(classeAbc);
        return zerado && !aceitaFicarSemEstoque && itemImportante;
    }

    /** Aplica o piso de urgência quando a regra dispara; senão devolve o grau do fuzzy intacto. */
    public static double aplicarPiso(double grauFuzzy, boolean regraDisparou) {
        return regraDisparou ? Math.max(grauFuzzy, URGENCIA_MINIMA) : grauFuzzy;
    }
}
