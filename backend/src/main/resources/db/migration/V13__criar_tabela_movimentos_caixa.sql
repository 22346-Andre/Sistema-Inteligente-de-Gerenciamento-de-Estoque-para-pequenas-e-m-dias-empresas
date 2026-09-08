-- Livro-caixa: entradas e saídas reais de dinheiro. Alimenta o saldo de
-- Disponibilidades do Balanço Patrimonial e a Demonstração de Fluxo de
-- Caixa (DFC). A maioria dos registros nasce automaticamente (venda não
-- fiado, fiado pago, despesa paga) — ver MovimentoCaixa.java.
CREATE TABLE movimentos_caixa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    origem VARCHAR(30) NOT NULL,
    valor DECIMAL(12,2) NOT NULL,
    descricao VARCHAR(255) NULL,
    data_movimento DATETIME NOT NULL,
    CONSTRAINT fk_movimentos_caixa_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
);

CREATE INDEX idx_movimentos_caixa_empresa_id ON movimentos_caixa (empresa_id);
CREATE INDEX idx_movimentos_caixa_data ON movimentos_caixa (data_movimento);
