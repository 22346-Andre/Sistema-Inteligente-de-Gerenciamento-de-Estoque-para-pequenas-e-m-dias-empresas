-- Rastreia quando o e-mail de aviso de inatividade ("sua conta será
-- apagada em 30 dias") foi enviado. NULL enquanto nenhum aviso foi disparado
-- pra essa empresa. Usado por CleanService pra dar carência de 30 dias antes
-- de apagar de verdade, em vez de apagar direto ao completar o prazo de
-- inatividade (ver histórico do job apagarEmpresasInativas).
ALTER TABLE empresas
    ADD COLUMN aviso_inatividade_enviado_em DATETIME NULL;
