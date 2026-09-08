package com.smartstock.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmpresaDTO {

    @NotBlank(message = "O CNPJ é obrigatório")
    private String cnpj;

    @NotBlank(message = "A Razão Social é obrigatória")
    private String razaoSocial;

    private String nomeFantasia;

    @NotBlank(message = "O e-mail de contato é obrigatório para os alertas de estoque")
    @Email(message = "Formato de e-mail inválido")
    private String emailContato;
}