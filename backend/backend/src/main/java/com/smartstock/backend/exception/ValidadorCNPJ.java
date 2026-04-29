package com.smartstock.backend.exception;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador customizado para CNPJ.
 * Verifica:
 * - Se tem exatamente 14 dígitos
 * - Se os dígitos verificadores estão corretos
 */
public class ValidadorCNPJ implements ConstraintValidator<ValidCNPJ, String> {

    @Override
    public void initialize(ValidCNPJ constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Se for nulo ou vazio, deixa para @NotBlank validar
        if (value == null || value.isEmpty()) {
            return true;
        }

        // Remove caracteres especiais (pontos, barras, hífens)
        String cnpjLimpo = value.replaceAll("[^0-9]", "");

        // Verifica se tem exatamente 14 dígitos
        if (cnpjLimpo.length() != 14) {
            addConstraintViolation(context, "CNPJ deve conter exatamente 14 dígitos");
            return false;
        }

        // Verifica se não é uma sequência repetida (ex: 11111111111111)
        if (cnpjLimpo.matches("(\\d)\\1{13}")) {
            addConstraintViolation(context, "CNPJ inválido: sequência repetida");
            return false;
        }

        // Pesos para o cálculo dos dígitos verificadores
        int[] pesosPrimeiroDigito = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesosSegundoDigito = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        // Calcula e valida o primeiro dígito verificador
        int primeiroDigitoCalculado = calcularDigito(cnpjLimpo.substring(0, 12), pesosPrimeiroDigito);
        if (Character.getNumericValue(cnpjLimpo.charAt(12)) != primeiroDigitoCalculado) {
            addConstraintViolation(context, "CNPJ inválido: dígito verificador incorreto");
            return false;
        }

        // Calcula e valida o segundo dígito verificador
        int segundoDigitoCalculado = calcularDigito(cnpjLimpo.substring(0, 13), pesosSegundoDigito);
        if (Character.getNumericValue(cnpjLimpo.charAt(13)) != segundoDigitoCalculado) {
            addConstraintViolation(context, "CNPJ inválido: dígito verificador incorreto");
            return false;
        }

        return true;
    }

    private int calcularDigito(String trecho, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < trecho.length(); i++) {
            soma += Character.getNumericValue(trecho.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return (resto < 2) ? 0 : 11 - resto;
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addConstraintViolation();
    }
}
