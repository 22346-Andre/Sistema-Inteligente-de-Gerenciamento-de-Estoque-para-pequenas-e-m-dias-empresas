package com.smartstock.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoDTO {

    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    private String descricao;

    @NotNull(message = "O preço é obrigatório")
    @Min(value = 0, message = "Preço não pode ser negativo")
    private BigDecimal preco;

    @NotNull(message = "A quantidade é obrigatória")
    @Min(value = 0, message = "Quantidade não pode ser negativa")
    private Integer quantidade;

    private Integer estoqueMinimo;

    private String ncm;
    private String unidade;


}