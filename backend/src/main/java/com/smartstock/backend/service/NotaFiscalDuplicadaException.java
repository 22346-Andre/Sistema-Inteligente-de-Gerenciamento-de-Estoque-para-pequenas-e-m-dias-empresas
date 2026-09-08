package com.smartstock.backend.service;

public class NotaFiscalDuplicadaException extends RuntimeException {
    public NotaFiscalDuplicadaException(String message) {
        super(message);
    }
}