package com.smartstock.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FixBancoDados {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void criarTabelaFaltando() {
        try {
            // Adicionamos o "id BIGINT AUTO_INCREMENT PRIMARY KEY" para o Aiven aceitar!
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS produto_impostos (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "produto_id BIGINT NOT NULL, " +
                    "sigla VARCHAR(255), " +
                    "esfera VARCHAR(255), " +
                    "aliquota DECIMAL(10,2), " +
                    "CONSTRAINT fk_produto_impostos_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)" +
                    ")");
            System.out.println("✅ Tabela produto_impostos criada na marra com sucesso!");
        } catch (Exception e) {
            System.out.println("⚠️ Aviso do banco de dados: " + e.getMessage());
        }
    }
}