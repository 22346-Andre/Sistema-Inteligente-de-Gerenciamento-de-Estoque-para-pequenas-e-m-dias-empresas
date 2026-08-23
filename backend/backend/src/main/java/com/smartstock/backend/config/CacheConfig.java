package com.smartstock.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache em memória (Caffeine) pra leituras pesadas de dashboard/relatórios
 * que não precisam ser em tempo real ao segundo (dashboard, curva ABC,
 * estoque encalhado, estatísticas).
 *
 * TTL curto (60s) de propósito: em vez de caçar todo lugar que insere/edita
 * produto ou movimentação pra invalidar o cache manualmente (frágil e fácil
 * de esquecer um caminho, ex.: importação de NF-e, PDV, ajuste manual de
 * estoque), a gente aceita até 60s de defasagem no dashboard. Isso resolve
 * o gargalo de leitura repetida sem introduzir risco de mostrar dado
 * "travado" por muito tempo.
 *
 * Se algum relatório específico precisar ser sempre exato (ex.: saldo antes
 * de fechar um caixa), NÃO cacheie aquele método — deixe sem @Cacheable.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "dashboardResumo",
                "dashboardGrafico",
                "curvaAbc",
                "matrizAbc",
                "giroEstoque",
                "estoqueEncalhado",
                "estatisticas"
        );
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .maximumSize(500)
                        .recordStats()
        );
        return cacheManager;
    }
}
