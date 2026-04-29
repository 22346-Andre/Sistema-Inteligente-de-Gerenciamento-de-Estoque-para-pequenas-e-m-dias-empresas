package com.smartstock.backend.dto;

import com.smartstock.backend.exception.ValidCNPJ;
import com.smartstock.backend.exception.ValidSenha;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistroEmpresaDTO {

    // --- DADOS DA EMPRESA ---
    @NotBlank(message = "A Razão Social é obrigatória")
    private String razaoSocial;

    private String nomeFantasia;

    @NotBlank(message = "O CNPJ é obrigatório")
    @ValidCNPJ(message = "O CNPJ informado é inválido")
    private String cnpj;

    private String emailContato;

    @NotBlank(message = "O telefone da empresa é obrigatório")
    @Size(min = 10, message = "O telefone deve ter pelo menos 10 dígitos")
    private String telefoneEmpresa;

    // --- DADOS DO DONO (ADMIN) ---
    @NotBlank(message = "O nome do dono é obrigatório")
    private String nomeDono;

    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail é obrigatório")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @ValidSenha(message = "A senha deve ter no mínimo 6 caracteres")
    private String senha;

    @NotBlank(message = "O celular do administrador é obrigatório")
    @Size(min = 10, message = "O celular deve ter pelo menos 10 dígitos")
    private String telefoneAdmin;


    public String getCnpj() {
        return this.cnpj;
    }
}