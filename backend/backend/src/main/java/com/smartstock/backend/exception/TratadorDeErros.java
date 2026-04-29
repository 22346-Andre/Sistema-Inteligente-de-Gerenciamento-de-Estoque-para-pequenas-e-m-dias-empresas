package com.smartstock.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorDeErros {

    // 1. CAPTURA ERRO DE LOGIN (E-mail ou Senha errados)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> tratarErroDeCredenciais(BadCredentialsException ex) {
        Map<String, String> resposta = new HashMap<>();

        resposta.put("erro", "Acesso negado: E-mail ou senha incorretos.");

        // Retorna 401 (Unauthorized)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resposta);
    }

    // 2. CAPTURA ERROS DE VALIDAÇÃO DO DTO (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarErrosDeValidacao(MethodArgumentNotValidException ex) {
        Map<String, Object> resposta = new HashMap<>();
        
        // Coleta todos os erros de validação
        Map<String, String> erros = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage(),
                        (existente, novo) -> existente + "; " + novo
                ));

        resposta.put("erro", "Validação falhou");
        resposta.put("detalhes", erros);

        // Retorna 400 (Bad Request)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resposta);
    }

    // 3. CAPTURA ERROS GERAIS DO SISTEMA (Os RuntimeExceptions dos Services)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> tratarErrosDeRegraDeNegocio(RuntimeException ex) {
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", ex.getMessage());

        // Retorna 400 (Bad Request) - Indica que a requisição não cumpriu uma regra de negócio
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resposta);
    }
}
