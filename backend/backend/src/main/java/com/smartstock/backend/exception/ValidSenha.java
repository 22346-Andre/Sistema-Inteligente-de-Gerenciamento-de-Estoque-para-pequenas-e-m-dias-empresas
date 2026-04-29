package com.smartstock.backend.exception;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Anotação para validar Senha.
 * Verifica se a senha tem no mínimo 6 caracteres.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorSenha.class)
@Documented
public @interface ValidSenha {
    String message() default "A senha deve ter no mínimo 6 caracteres";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
