package com.smartstock.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class TratadorDeErros {

    // Sempre que um RuntimeException for lançado no sistema, ele captura!
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> tratarErrosDeRegraDeNegocio(RuntimeException ex) {


        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", ex.getMessage());


        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resposta);
    }
}