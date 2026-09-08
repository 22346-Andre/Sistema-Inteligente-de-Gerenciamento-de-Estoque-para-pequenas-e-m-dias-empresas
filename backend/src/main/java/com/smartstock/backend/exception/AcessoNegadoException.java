package com.smartstock.backend.exception;


 
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagemInterna) {
        super(mensagemInterna);
    }
}