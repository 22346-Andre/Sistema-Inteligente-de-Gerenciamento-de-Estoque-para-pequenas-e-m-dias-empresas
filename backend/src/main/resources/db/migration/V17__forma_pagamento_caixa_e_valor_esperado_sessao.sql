-- Forma de pagamento no MovimentoCaixa: sem isso não dava pra separar
-- quanto de uma venda foi em dinheiro de verdade (pra conferência de gaveta)
-- do que foi cartão/PIX (conta pro saldo da empresa, mas nunca fica na
-- gaveta física).
ALTER TABLE movimentos_caixa
    ADD COLUMN forma_pagamento VARCHAR(20) NULL;

-- Valor esperado em dinheiro, calculado no momento do fechamento do turno
-- (fundo de troco + vendas em espécie do período) — guardado junto com o
-- valor contado, pra manter o histórico de "esperado x contado" de cada
-- fechamento, mesmo que os lançamentos de caixa sejam reorganizados depois.
ALTER TABLE sessoes_caixa
    ADD COLUMN valor_esperado DECIMAL(12,2) NULL;
