
ALTER TABLE usuarios ADD COLUMN reset_senha_token VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN reset_senha_expiracao DATETIME;

CREATE INDEX idx_usuarios_reset_senha_token ON usuarios (reset_senha_token);


ALTER TABLE empresas ADD COLUMN chave_pix VARCHAR(140);
