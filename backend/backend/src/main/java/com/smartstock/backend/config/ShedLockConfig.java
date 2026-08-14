package com.smartstock.backend.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Garante que os jobs @Scheduled (VerificadorEstoqueService, CleanService,
 * LimpezaDadosScheduler) rodem em uma única instância por vez, mesmo com o
 * backend escalado horizontalmente atrás de um load balancer. Sem isso, cada
 * instância dispara o job de forma independente -- o caso mais visível é o
 * cliente receber o e-mail de resumo diário duplicado, uma cópia por
 * instância.
 *
 * O lock vive na tabela "shedlock" (ver migration V10), então não precisa de
 * Redis só pra isso.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
