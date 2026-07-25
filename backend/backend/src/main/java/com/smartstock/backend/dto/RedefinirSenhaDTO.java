package com.smartstock.backend.dto;

import com.smartstock.backend.exception.ValidSenha;
import jakarta.validation.constraints.NotBlank;

public record RedefinirSenhaDTO(
        @NotBlank(message = "Token de recuperação é obrigatório")
        String token,

        @NotBlank(message = "Nova senha é obrigatória")
        @ValidSenha
        String novaSenha
) {
}
