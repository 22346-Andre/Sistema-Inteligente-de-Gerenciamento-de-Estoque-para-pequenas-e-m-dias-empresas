package com.smartstock.backend.exception;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador customizado para Senha.
 * Verifica:
 * - Se tem no mínimo 6 caracteres
 * - Se não é nula ou vazia
 */
public class ValidadorSenha implements ConstraintValidator<ValidSenha, String> {

    @Override
    public void initialize(ValidSenha constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Se for nulo ou vazio, deixa para @NotBlank validar
        if (value == null || value.isEmpty()) {
            return true;
        }

        // Verifica se tem no mínimo 6 caracteres
        if (value.length() < 6) {
            addConstraintViolation(context, "A senha deve ter no mínimo 6 caracteres");
            return false;
        }

        return true;
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addConstraintViolation();
    }
}
