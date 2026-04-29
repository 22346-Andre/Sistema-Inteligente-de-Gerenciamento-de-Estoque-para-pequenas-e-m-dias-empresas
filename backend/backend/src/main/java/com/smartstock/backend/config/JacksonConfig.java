package com.smartstock.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Cria o tradutor JSON do zero
        ObjectMapper mapper = new ObjectMapper();

        // Regista oficialmente o módulo que sabe ler "LocalDateTime"
        mapper.registerModule(new JavaTimeModule());

        // Proíbe o sistema de enviar a data como um código numérico bizarro
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}