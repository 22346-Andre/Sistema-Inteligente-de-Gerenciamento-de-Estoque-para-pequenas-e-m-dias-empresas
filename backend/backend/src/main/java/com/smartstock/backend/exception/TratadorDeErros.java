package com.smartstock.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger logger = LoggerFactory.getLogger(TratadorDeErros.class);

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

    // 3. RECURSO NÃO ENCONTRADO — 404, mensagem genérica pro cliente (detalhe só no log)
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        logger.info("Recurso não encontrado: {}", ex.getMessage());
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", "Recurso não encontrado.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resposta);
    }

    // 4. ACESSO NEGADO (multi-tenant / permissão) — 403, mensagem genérica pro cliente
    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Map<String, String>> tratarAcessoNegado(AcessoNegadoException ex) {
        logger.warn("Tentativa de acesso negado: {}", ex.getMessage());
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", "Acesso negado.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resposta);
    }

    // 5. ESTOQUE INSUFICIENTE — 409, mensagem específica é segura e útil pro usuário
    @ExceptionHandler(EstoqueInsuficienteException.class)
    public ResponseEntity<Map<String, String>> tratarEstoqueInsuficiente(EstoqueInsuficienteException ex) {
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(resposta);
    }

    // 6. VIOLAÇÃO DE INTEGRIDADE REFERENCIAL (Ex: Excluir fornecedor em uso) — 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> tratarViolacaoIntegridade(DataIntegrityViolationException ex) {
        String causaRaiz = ex.getMostSpecificCause().getMessage();
        logger.warn("Violação de integridade referencial: {}", causaRaiz);

        Map<String, String> resposta = new HashMap<>();

       
        boolean violacaoDeUnicidade = causaRaiz != null && (
                causaRaiz.toLowerCase().contains("duplicate entry")
                        || causaRaiz.toLowerCase().contains("unique constraint")
                        || causaRaiz.toLowerCase().contains("violates unique")
        );

        if (violacaoDeUnicidade) {
            resposta.put("erro", "Já existe um registro com esse mesmo valor (e-mail, CNPJ ou código de barras já cadastrado).");
        } else {
            resposta.put("erro", "Não é possível excluir este registro pois ele está vinculado a outros dados do sistema.");
        }

        // Retorna 409 (Conflict) em vez de 400
        return ResponseEntity.status(HttpStatus.CONFLICT).body(resposta);
    }

    // 7. CAPTURA ERROS GERAIS DO SISTEMA (RuntimeException genérica ainda não migrada
    // pra uma exceção específica acima). Mantido como rede de segurança.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> tratarErrosDeRegraDeNegocio(RuntimeException ex) {
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", ex.getMessage());

        // Retorna 400 (Bad Request) - Indica que a requisição não cumpriu uma regra de negócio
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resposta);
    }

    // 8. RESPONSE STATUS EXCEPTION (Tratamento para status HTTP lançados manualmente)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> tratarResponseStatus(ResponseStatusException ex) {
        Map<String, String> resposta = new HashMap<>();
        resposta.put("erro", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(resposta);
    }
}