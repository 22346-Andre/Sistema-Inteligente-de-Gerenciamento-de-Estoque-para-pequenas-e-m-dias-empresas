-- Capital Social — última peça pro Patrimônio Líquido do Balanço
-- Patrimonial. NULL até o dono do negócio preencher (o sistema não tem
-- como descobrir esse valor sozinho); o relatório trata NULL como zero.
ALTER TABLE empresas
    ADD COLUMN capital_social DECIMAL(14,2) NULL;
