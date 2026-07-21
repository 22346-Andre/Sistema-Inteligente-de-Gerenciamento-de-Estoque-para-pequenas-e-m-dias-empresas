package com.smartstock.backend.exception;


public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagemInterna) {
        super(mensagemInterna);
    }
}