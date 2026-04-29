package com.smartstock.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Configuração global de fuso horário para a aplicação.
 * Define o fuso horário como America/Sao_Paulo (UTC-3 - Brasília)
 */
@Configuration
public class TimezoneConfig {

    public TimezoneConfig() {
        // Define o fuso horário padrão da JVM para Brasília
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        System.setProperty("user.timezone", "America/Sao_Paulo");
    }

    /**
     * Configura o ObjectMapper do Jackson para usar o fuso horário correto
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
