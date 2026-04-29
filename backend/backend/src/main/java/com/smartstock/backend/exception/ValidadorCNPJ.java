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

        // Calcula e valida o primeiro dígito verificador
        int primeiroDigito = calcularDigitoVerificador(cnpjLimpo.substring(0, 12), 5);
        if (Integer.parseInt(cnpjLimpo.substring(12, 13)) != primeiroDigito) {
            addConstraintViolation(context, "CNPJ inválido: dígito verificador incorreto");
            return false;
        }

        // Calcula e valida o segundo dígito verificador
        int segundoDigito = calcularDigitoVerificador(cnpjLimpo.substring(0, 13), 6);
        if (Integer.parseInt(cnpjLimpo.substring(13, 14)) != segundoDigito) {
            addConstraintViolation(context, "CNPJ inválido: dígito verificador incorreto");
            return false;
        }

        return true;
    }

    /**
     * Calcula o dígito verificador do CNPJ.
     *
     * @param cnpj     Os primeiros 12 ou 13 dígitos do CNPJ
     * @param posicaoInicial A posição inicial para o cálculo (5 para o primeiro dígito, 6 para o segundo)
     * @return O dígito verificador calculado
     */
    private int calcularDigitoVerificador(String cnpj, int posicaoInicial) {
        int soma = 0;
        int multiplicador = posicaoInicial;

        for (char c : cnpj.toCharArray()) {
            soma += Character.getNumericValue(c) * multiplicador;
            multiplicador--;
            if (multiplicador == 0) {
                multiplicador = 9;
            }
        }

        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addConstraintViolation();
    }
}
