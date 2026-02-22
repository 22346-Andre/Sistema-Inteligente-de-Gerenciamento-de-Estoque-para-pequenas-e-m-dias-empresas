package com.smartstock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmpresaDTO {

    @NotBlank(message = "O CNPJ é obrigatório")
    private String cnpj;

    @NotBlank(message = "A Razão Social é obrigatória")
    private String razaoSocial;

    private String nomeFantasia;
}
