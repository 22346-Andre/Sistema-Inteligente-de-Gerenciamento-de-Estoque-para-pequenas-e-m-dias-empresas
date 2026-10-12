-- Segurança: PasswordResetService passou a guardar SHA-256(token) em vez do token em
-- claro. Tokens já emitidos (em claro) não batem mais com um hash, então ficariam
-- "presos" sem uso — zeramos os pendentes; quem precisar, pede recuperação de novo.
UPDATE usuarios
SET reset_senha_token = NULL, reset_senha_expiracao = NULL
WHERE reset_senha_token IS NOT NULL;
