package com.smartstock.backend.dto;
import lombok.Data;

@Data
public class AlterarSenhaDTO {
    private String senhaAtual;
    private String novaSenha;
}