package com.smartstock.backend.exception;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador customizado para Senha.
 * Delega a regra para PoliticaSenha, para não haver dois critérios divergentes
 * (aqui e em UsuarioService.alterarSenha/salvar).
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

        String erro = PoliticaSenha.validar(value);
        if (erro != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(erro).addConstraintViolation();
            return false;
        }

        return true;
    }
}
