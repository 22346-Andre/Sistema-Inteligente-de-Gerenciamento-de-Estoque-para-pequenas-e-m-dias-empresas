-- Rastreabilidade: quem fez cada movimentação de estoque e cada movimento de
-- caixa (a que horas já existia, via dataMovimentacao/dataMovimento).
-- Snapshot (id + nome), não FK pro usuário: se o funcionário for excluído
-- depois, o histórico continua legível em vez de virar "usuário null".
ALTER TABLE movimentacoes
    ADD COLUMN usuario_id BIGINT NULL,
    ADD COLUMN usuario_nome VARCHAR(150) NULL;

ALTER TABLE movimentos_caixa
    ADD COLUMN usuario_id BIGINT NULL,
    ADD COLUMN usuario_nome VARCHAR(150) NULL;

CREATE INDEX idx_movimentacoes_usuario_id ON movimentacoes (usuario_id);
CREATE INDEX idx_movimentos_caixa_usuario_id ON movimentos_caixa (usuario_id);
