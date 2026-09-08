package com.smartstock.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wakeup")
public class PingController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public String manterAcordado() {
        // Envia um comando super leve só para o Aiven saber que estamos vivos
        jdbcTemplate.execute("SELECT 1");
        return "✅ Render e Aiven estão 100% acordados!";
    }
}