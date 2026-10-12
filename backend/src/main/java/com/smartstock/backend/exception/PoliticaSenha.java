package com.smartstock.backend.exception;

/**
 * Regra de senha usada em todo lugar que grava uma senha: cadastro de empresa
 * (RegistroEmpresaDTO), "esqueci minha senha" (RedefinirSenhaDTO), troca de senha pelo
 * próprio usuário (UsuarioService.alterarSenha) e criação de funcionário (UsuarioService.salvar).
 * Fica num lugar só para não haver dois critérios divergentes.
 */
public final class PoliticaSenha {

    private static final int TAMANHO_MINIMO = 8;

    private PoliticaSenha() {
    }

    /** Mensagem de erro, ou null se a senha atende a regra. Não valida nulo/vazio (isso é @NotBlank). */
    public static String validar(String senha) {
        if (senha == null || senha.isEmpty()) {
            return null;
        }
        if (senha.length() < TAMANHO_MINIMO) {
            return "A senha deve ter no mínimo " + TAMANHO_MINIMO + " caracteres.";
        }
        boolean temLetra = senha.chars().anyMatch(Character::isLetter);
        boolean temDigito = senha.chars().anyMatch(Character::isDigit);
        if (!temLetra || !temDigito) {
            return "A senha deve ter letras e números.";
        }
        return null;
    }
}
