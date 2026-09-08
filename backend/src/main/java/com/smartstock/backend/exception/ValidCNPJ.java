package com.smartstock.backend.exception;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Anotação para validar CNPJ.
 * Verifica se o CNPJ tem 14 dígitos e se os dígitos verificadores estão corretos.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorCNPJ.class)
@Documented
public @interface ValidCNPJ {
    String message() default "CNPJ inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
