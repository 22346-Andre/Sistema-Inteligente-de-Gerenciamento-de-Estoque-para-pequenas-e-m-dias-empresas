package com.smartstock.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistroEmpresaDTO {

    // --- DADOS DA EMPRESA ---
    @NotBlank(message = "A Razão Social é obrigatória")
    private String nomeEmpresa;


    @NotBlank(message = "O Nome Fantasia é obrigatório")
    private String nomeFantasia;

    @NotBlank(message = "O CNPJ é obrigatório")
    private String cnpj;

    @NotBlank(message = "O e-mail de contato da empresa é obrigatório")
    @Email(message = "E-mail de contato inválido")
    private String emailContato;


    private String telefoneEmpresa;

    // --- DADOS DO DONO (ADMIN) ---
    @NotBlank(message = "O nome do administrador é obrigatório")
    private String nomeAdmin;

    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail do administrador é obrigatório")
    private String emailAdmin;

    @NotBlank(message = "A senha é obrigatória")
    private String senhaAdmin;

    private String telefoneAdmin;
}