-- Turno de caixa por operador: quando abriu, quando fechou, e os valores
-- informados (fundo de troco na abertura, contagem na hora do fechamento).
-- Uma pessoa só tem uma sessão aberta (data_fechamento NULL) por vez —
-- controlado em código (SessaoCaixaService), não por constraint aqui.
CREATE TABLE sessoes_caixa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    usuario_abertura_id BIGINT NOT NULL,
    usuario_abertura_nome VARCHAR(150) NOT NULL,
    data_abertura DATETIME NOT NULL,
    valor_abertura DECIMAL(12,2) NULL,
    data_fechamento DATETIME NULL,
    usuario_fechamento_id BIGINT NULL,
    usuario_fechamento_nome VARCHAR(150) NULL,
    valor_fechamento_informado DECIMAL(12,2) NULL,
    observacao VARCHAR(255) NULL,
    CONSTRAINT fk_sessoes_caixa_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
);

CREATE INDEX idx_sessoes_caixa_empresa_id ON sessoes_caixa (empresa_id);
CREATE INDEX idx_sessoes_caixa_usuario_abertura_id ON sessoes_caixa (usuario_abertura_id);
