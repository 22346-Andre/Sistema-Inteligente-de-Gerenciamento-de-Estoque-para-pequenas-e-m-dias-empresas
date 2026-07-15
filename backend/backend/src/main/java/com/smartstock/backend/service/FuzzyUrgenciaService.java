package com.smartstock.backend.service;

import net.sourceforge.jFuzzyLogic.FIS;
import org.springframework.stereotype.Service;

@Service
public class FuzzyUrgenciaService {

    private static final String FCL_RULES =
        "FUNCTION_BLOCK sugestao_compra\n" +
        "\n" +
        "VAR_INPUT\n" +
        "    nivel_estoque : REAL; (* % do estoque minimo: atual/minimo*100 *)\n" +
        "    giro_vendas : REAL;   (* unidades vendidas (saidas) nos ultimos 30 dias *)\n" +
        "    prazo_entrega : REAL; (* dias que o fornecedor demora *)\n" +
        "END_VAR\n" +
        "\n" +
        "VAR_OUTPUT\n" +
        "    urgencia : REAL; (* 0 a 100 *)\n" +
        "END_VAR\n" +
        "\n" +
        "FUZZIFY nivel_estoque\n" +
        "    TERM baixo := (0, 1) (50, 1) (100, 0);\n" +
        "    TERM adequado := (80, 0) (100, 1) (150, 1) (200, 0);\n" +
        "    TERM alto := (150, 0) (200, 1) (300, 1);\n" +
        "END_FUZZIFY\n" +
        "\n" +
        "FUZZIFY giro_vendas\n" +
        "    TERM lento := (0, 1) (20, 1) (50, 0);\n" +
        "    TERM moderado := (30, 0) (50, 1) (80, 0);\n" +
        "    TERM rapido := (60, 0) (80, 1) (200, 1);\n" +
        "END_FUZZIFY\n" +
        "\n" +
        "FUZZIFY prazo_entrega\n" +
        "    TERM rapido := (0, 1) (3, 1) (7, 0);\n" +
        "    TERM aceitavel := (5, 0) (10, 1) (15, 0);\n" +
        "    TERM demorado := (12, 0) (20, 1) (30, 1);\n" +
        "END_FUZZIFY\n" +
        "\n" +
        "DEFUZZIFY urgencia\n" +
        "    TERM nula := (0, 1) (15, 1) (30, 0);\n" +
        "    TERM baixa := (15, 0) (30, 1) (50, 0);\n" +
        "    TERM media := (35, 0) (55, 1) (75, 0);\n" +
        "    TERM alta := (60, 0) (80, 1) (100, 1);\n" +
        "    METHOD : COG;\n" +
        "    DEFAULT := 0;\n" +
        "END_DEFUZZIFY\n" +
        "\n" +
        "RULEBLOCK regras\n" +
        "    AND : MIN;\n" +
        "    ACT : MIN;\n" +
        "    ACCU : MAX;\n" +
        "\n" +
        "    RULE 1 : IF nivel_estoque IS baixo AND giro_vendas IS rapido AND prazo_entrega IS demorado THEN urgencia IS alta;\n" +
        "    RULE 2 : IF nivel_estoque IS baixo AND giro_vendas IS rapido AND prazo_entrega IS aceitavel THEN urgencia IS alta;\n" +
        "    RULE 3 : IF nivel_estoque IS baixo AND giro_vendas IS moderado THEN urgencia IS media;\n" +
        "    RULE 4 : IF nivel_estoque IS baixo AND giro_vendas IS lento THEN urgencia IS baixa;\n" +
        "    RULE 5 : IF nivel_estoque IS adequado AND giro_vendas IS rapido THEN urgencia IS media;\n" +
        "    RULE 6 : IF nivel_estoque IS adequado AND giro_vendas IS moderado THEN urgencia IS baixa;\n" +
        "    RULE 7 : IF nivel_estoque IS adequado AND giro_vendas IS lento THEN urgencia IS nula;\n" +
        "    RULE 8 : IF nivel_estoque IS alto THEN urgencia IS nula;\n" +
        "    RULE 9 : IF nivel_estoque IS baixo AND prazo_entrega IS demorado THEN urgencia IS alta;\n" +
        "END_RULEBLOCK\n" +
        "\n" +
        "END_FUNCTION_BLOCK";

    /**
     * Avalia a urgência de compra para um produto.
     * Cria um FIS novo por chamada (evita estado compartilhado entre threads/produtos).
     */
    public double calcularUrgencia(double nivelEstoquePct, double giroVendas, double prazoEntregaDias) {
        FIS fis = FIS.createFromString(FCL_RULES, true);

        fis.setVariable("nivel_estoque", Math.max(0, nivelEstoquePct));
        fis.setVariable("giro_vendas", Math.max(0, giroVendas));
        fis.setVariable("prazo_entrega", Math.max(0, prazoEntregaDias));

        fis.evaluate();

        return fis.getVariable("urgencia").getValue();
    }
}