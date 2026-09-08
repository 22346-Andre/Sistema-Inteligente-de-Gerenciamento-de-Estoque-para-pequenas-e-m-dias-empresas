-- Despesa / Conta a Pagar — mesma estrutura de contas_receber (status
-- PENDENTE/PAGO/ATRASADO), só que do lado das obrigações da empresa em vez
-- do lado dos clientes. Alimenta o DRE (despesas operacionais) e o Passivo
-- Circulante do Balanço Patrimonial.
CREATE TABLE despesas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    categoria VARCHAR(60) NOT NULL,
    valor DECIMAL(12,2) NOT NULL,
    data_vencimento DATE NOT NULL,
    data_pagamento DATE NULL,
    fornecedor_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    data_criacao DATETIME NULL,
    CONSTRAINT fk_despesas_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_despesas_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id)
);

CREATE INDEX idx_despesas_empresa_id ON despesas (empresa_id);
CREATE INDEX idx_despesas_status ON despesas (status);
