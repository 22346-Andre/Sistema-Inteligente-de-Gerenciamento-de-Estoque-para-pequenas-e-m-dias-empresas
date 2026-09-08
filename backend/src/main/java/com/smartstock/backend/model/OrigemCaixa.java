package com.smartstock.backend.model;

/**
 * De onde veio (ou pra onde foi) o dinheiro — usado pra agrupar a DFC por
 * atividade (operacional vs. financiamento) sem precisar de um plano de
 * contas completo.
 */
public enum OrigemCaixa {
    VENDA_PDV,          // ENTRADA — venda à vista/cartão/PIX (não fiado)
    RECEBIMENTO_FIADO,  // ENTRADA — cliente pagou uma conta a receber
    PAGAMENTO_DESPESA,  // SAIDA   — uma despesa foi marcada como paga
    APORTE_SOCIO,       // ENTRADA — sócio colocou dinheiro na empresa (lançamento manual)
    RETIRADA_SOCIO,     // SAIDA   — sócio retirou dinheiro da empresa (lançamento manual)
    COMPRA_MERCADORIA,  // SAIDA   — entrada de estoque paga à vista (ver ProdutoService.adicionarLote)
    OUTRO               // lançamento manual avulso, sem categoria melhor
}
