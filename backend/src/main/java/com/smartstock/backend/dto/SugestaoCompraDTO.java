package com.smartstock.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SugestaoCompraDTO {
    private Long produtoId;
    private String urgencia; // "URGENTE" ou "ATENCAO" (mantido p/ compatibilidade com a tela)
    private Double grauUrgencia; //  0 a 100, para barra de progresso / ordenação fina
    private String nomeProduto;
    private String nomeFornecedor;
    private Integer quantidadeAtual;
    private Integer estoqueMinimo;
    private Integer quantidadeSugerida;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private String telefoneFornecedor;

  
    private String nivelEstoqueLabel;   // "Baixo" | "Adequado" | "Alto"
    private Integer giroVendas;          // unidades vendidas nos últimos 30 dias
    private String giroVendasLabel;      // "Lento" | "Moderado" | "Rápido"
    private Double prazoEntregaDias;
    private String prazoEntregaLabel;    // "Rápido" | "Aceitável" | "Demorado"

    
    private String prazoEntregaOrigem;

    // "RUPTURA" = o botão de pânico elevou a urgência (estoque zerado de item classe A/B);
    // "FUZZY" = urgência veio só do motor fuzzy.
    private String urgenciaOrigem;
    private String classeAbc;                // "A" | "B" | "C" | null (só consultada em estoque zerado)

    // Explicação da quantidade sugerida, mostrada ao gestor.
    private Double consumoDiarioMedio;       // unidades/dia (vendas dos últimos 30 dias / 30)
    private String quantidadeSugeridaMotivo;
}