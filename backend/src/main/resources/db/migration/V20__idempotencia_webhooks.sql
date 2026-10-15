-- Cada item do pedido é gravado na mesma transação da baixa de estoque.
-- A chave única impede que reentregas do mesmo webhook repitam a baixa.
CREATE TABLE webhook_itens_processados (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    origem VARCHAR(64) NOT NULL,
    pedido_id VARCHAR(128) NOT NULL,
    codigo_barras VARCHAR(100) NOT NULL,
    quantidade INT NOT NULL,
    chave_movimentacao VARCHAR(44) NOT NULL,
    processado_em DATETIME NOT NULL,
    CONSTRAINT uk_webhook_item_pedido
        UNIQUE (empresa_id, origem, pedido_id, codigo_barras),
    CONSTRAINT fk_webhook_item_empresa
        FOREIGN KEY (empresa_id) REFERENCES empresas (id)
);
