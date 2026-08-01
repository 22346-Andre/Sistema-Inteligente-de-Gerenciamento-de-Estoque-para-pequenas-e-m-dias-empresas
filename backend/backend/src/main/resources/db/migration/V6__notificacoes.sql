
CREATE TABLE notificacoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    tipo VARCHAR(60) NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    mensagem TEXT NOT NULL,
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao DATETIME NOT NULL,
    CONSTRAINT fk_notificacoes_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id)
);

CREATE INDEX idx_notificacoes_empresa_lida ON notificacoes (empresa_id, lida);
