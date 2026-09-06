package com.smartstock.backend.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import com.smartstock.backend.model.Imposto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    // 🆕 Validade do lote inicial (opcional). Usado tanto pelo CSV quanto pelo
    // formulário manual de "novo produto" — se não vier, o lote fica sem
    // validade (NULL), nunca mais um chute de "hoje + 1 ano" pra tudo.
    @CsvBindByName(column = "dataValidade")
    @CsvDate("yyyy-MM-dd")
    private LocalDate dataValidade;

    @CsvBindByName(column = "ncm")
    private String ncm;

    @CsvBindByName(column = "cfop")
    private String cfop;

    private List<Imposto> impostos;

    @CsvBindByName(column = "icms")
    private BigDecimal icms;

    @CsvBindByName(column = "ipi")
    private BigDecimal ipi;

    @CsvBindByName(column = "pis")
    private BigDecimal pis;

    @CsvBindByName(column = "cofins")
    private BigDecimal cofins;

    @CsvBindByName(column = "unidade")
    private String unidade;

    // Usado pelo formulário manual de produto (dropdown de fornecedor já cadastrado)
    @CsvBindByName(column = "fornecedorId")
    private Long fornecedorId;

    // Usados pela importação em massa (CSV): informe nome e/ou CNPJ do fornecedor
    // em vez do ID — se não existir, é cadastrado automaticamente.
    @CsvBindByName(column = "fornecedorNome")
    private String fornecedorNome;

    @CsvBindByName(column = "fornecedorCnpj")
    private String fornecedorCnpj;

    @CsvBindByName(column = "finalidadeEstoque")
    private String finalidadeEstoque;
}