package com.smartstock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.br.CNPJ;

@Data
public class FornecedorDTO {

    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @NotBlank(message = "O CNPJ é obrigatório")
    @CNPJ(message = "CNPJ inválido") // Valida se o número é real!
    private String cnpj;

    private String telefone;
    private String email;
    private String endereco;
}
