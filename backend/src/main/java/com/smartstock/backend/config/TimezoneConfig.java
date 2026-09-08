package com.smartstock.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

@Configuration
public class TimezoneConfig {

    public TimezoneConfig() {
        // Define o fuso horário padrão da máquina (Render) para Brasília
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        System.setProperty("user.timezone", "America/Sao_Paulo");
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // Usa o construtor do Spring para não perder outras otimizações de fábrica
        ObjectMapper mapper = builder.createXmlMapper(false).build();

        // 1. Configura o fuso horário do Brasil no tradutor JSON
        mapper.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

        // 2. A MÁGICA: Ensina o Java a converter as datas modernas (LocalDateTime)
        mapper.registerModule(new JavaTimeModule());

        // 3. Proíbe o sistema de enviar a data como números quebrados (milissegundos)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}