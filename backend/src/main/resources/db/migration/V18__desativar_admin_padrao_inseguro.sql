-- Segurança: a classe AdminUserConfig (removida) criava, a cada inicialização,
-- o usuário admin@smartstock.com com a senha fixa "123456" e perfil ADMIN.
-- Se essa conta existir no banco, ela é desativada: a senha vira um valor que
-- não é um hash BCrypt válido, então nenhum login (nem por senha, nem por
-- "esqueci minha senha" sem acesso ao e-mail) autentica com ela.
-- Só atinge a conta órfã (sem empresa); não apaga linhas, então não quebra FKs.
UPDATE usuarios
SET senha = '!conta-desativada'
WHERE email = 'admin@smartstock.com'
  AND empresa_id IS NULL;
