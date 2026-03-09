package com.smartstock.backend.dto;

import com.opencsv.bean.CsvBindByName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoDTO {

    @NotBlank(message = "O nome é obrigatório")
    @CsvBindByName(column = "nome")
    private String nome;

    @CsvBindByName(column = "descricao")
    private String descricao;

    @CsvBindByName(column = "codigoBarras")
    private String codigoBarras;

    @CsvBindByName(column = "categoria")
    private String categoria;

    @Min(value = 0, message = "Preço de custo não pode ser negativo")
    @CsvBindByName(column = "precoCusto")
    private BigDecimal precoCusto;

    @Min(value = 0, message = "Preço de venda não pode ser negativo")
    @CsvBindByName(column = "precoVenda")
    private BigDecimal precoVenda;

    @CsvBindByName(column = "quantidade")
    private Integer quantidade;

    @CsvBindByName(column = "quantidadeMinima")
    private Integer quantidadeMinima;

    @CsvBindByName(column = "ncm")
    private String ncm;

    @CsvBindByName(column = "unidade")
    private String unidade;

    @CsvBindByName(column = "fornecedorId")
    private Long fornecedorId;
}