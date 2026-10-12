package com.smartstock.backend.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlterarSenhaDTO {
    @NotBlank(message = "Senha atual é obrigatória")
    private String senhaAtual;

    @NotBlank(message = "Nova senha é obrigatória")
    private String novaSenha;
}
