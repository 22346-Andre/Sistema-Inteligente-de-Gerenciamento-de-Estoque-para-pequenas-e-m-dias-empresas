package com.smartstock.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistroEmpresaDTO {

    // --- DADOS DA EMPRESA ---
    @NotBlank(message = "A Razão Social é obrigatória")
    private String razaoSocial;

    private String nomeFantasia;

    @NotBlank(message = "O CNPJ é obrigatório")
    private String cnpj;

    private String emailContato;

    private String telefoneEmpresa;

    // --- DADOS DO DONO (ADMIN) ---
    @NotBlank(message = "O nome do dono é obrigatório")
    private String nomeDono;

    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail é obrigatório")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    private String senha;

    private String telefoneAdmin;
}