ALTER TABLE contas_receber ADD COLUMN pix_correlation_id VARCHAR(60) NULL;
CREATE INDEX idx_contas_receber_pix_correlation_id ON contas_receber (pix_correlation_id);
