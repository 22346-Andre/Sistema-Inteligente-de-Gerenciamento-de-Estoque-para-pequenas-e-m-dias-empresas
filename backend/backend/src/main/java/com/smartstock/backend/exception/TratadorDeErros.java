package com.smartstock.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class TratadorDeErros {

    // 1. CAPTURA ERRO DE LOGIN (E-mail ou Senha errados)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> tratarErroDeCredenciais(BadCredentialsException ex) {
        Map<String, String> resposta = new HashMap<>();
        // Mensagem amigável para o Front-end mostrar na tela
        resposta.put("erro", "Acesso negado: E-mail ou senha incorretos.");

        // Retorna 401 (Unauthorized) - O padrão correto da web para falha de login
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resposta);
    }

    // 2. CAPTURA ERROS GERAIS DO SISTEMA (Os RuntimeExceptions dos Services)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> tratarErrosDeRegraDeNegocio(RuntimeException ex) {
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", ex.getMessage());

        // Retorna 400 (Bad Request) - Indica que a requisição não cumpriu uma regra de negócio
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resposta);
    }
}