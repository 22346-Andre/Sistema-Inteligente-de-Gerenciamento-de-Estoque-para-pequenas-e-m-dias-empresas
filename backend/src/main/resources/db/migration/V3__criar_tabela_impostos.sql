CREATE TABLE produto_impostos (
    produto_id BIGINT NOT NULL,
    sigla VARCHAR(255),
    esfera VARCHAR(255),
    aliquota DECIMAL(10,2),
    CONSTRAINT fk_produto_impostos_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
);