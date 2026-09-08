-- Tabela usada pelo ShedLock pra garantir que os @Scheduled jobs
-- (relatório diário, limpeza de empresas inativas, limpeza de movimentações)
-- rodem em UMA instância só quando o backend estiver escalado horizontalmente.
-- Formato exigido pelo shedlock-provider-jdbc-template.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
