package com.smartstock.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.br.CNPJ;

@Data
public class FornecedorDTO {

    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @NotBlank(message = "O CNPJ é obrigatório")
    @CNPJ(message = "CNPJ inválido")
    private String cnpj;

    private String telefone;
    private String email;
    private String endereco;

    @Min(value = 0, message = "Prazo de entrega não pode ser negativo")
    @Max(value = 90, message = "Prazo de entrega parece muito alto, confira o valor")
    private Integer prazoEntregaDias; // opcional — se null, o sistema assume 7 dias no cálculo de urgência

    
    private String categoriasFornecidas;
}