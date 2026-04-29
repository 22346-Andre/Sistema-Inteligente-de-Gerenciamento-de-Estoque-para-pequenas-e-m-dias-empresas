package com.smartstock.backend.dto;

import com.smartstock.backend.exception.ValidCNPJ;
import com.smartstock.backend.exception.ValidSenha;
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
    @ValidCNPJ(message = "CNPJ inválido ou dígitos verificadores incorretos")
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
    @ValidSenha(message = "A senha deve ter no mínimo 6 caracteres")
    private String senhaAdmin;

    private String telefoneAdmin;
}