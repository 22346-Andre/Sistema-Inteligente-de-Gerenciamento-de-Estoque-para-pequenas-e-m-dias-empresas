package com.smartstock.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistroEmpresaDTO {

    // --- DADOS DA EMPRESA ---
    @NotBlank(message = "A Razão Social é obrigatória")
    @JsonProperty("razaoSocial")
    private String nomeEmpresa;

    private String nomeFantasia;

    @NotBlank(message = "O CNPJ é obrigatório")
    private String cnpj;

    private String emailContato;

    private String telefoneEmpresa;

    // --- DADOS DO DONO (ADMIN) ---
    @NotBlank(message = "O nome do administrador é obrigatório")
    @JsonProperty("nomeDono")
    private String nomeAdmin;

    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail do administrador é obrigatório")
    @JsonProperty("email")
    private String emailAdmin;

    @NotBlank(message = "A senha é obrigatória")
    @JsonProperty("senha")
    private String senhaAdmin;

    private String telefoneAdmin;
}