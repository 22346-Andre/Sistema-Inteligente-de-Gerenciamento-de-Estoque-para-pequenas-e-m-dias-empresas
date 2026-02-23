package com.smartstock.backend.model;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor // O OpenCSV precisa de um construtor vazio para instanciar a classe
@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CsvBindByName(column = "NOME")
    private String nome;

    @CsvBindByName(column = "DESCRICAO")
    private String descricao;

    @CsvBindByName(column = "PRECO")
    private BigDecimal preco;

    @CsvBindByName(column = "QUANTIDADE")
    private Integer quantidade;

    @CsvBindByName(column = "ESTOQUE_MINIMO")
    @Column(name = "estoque_minimo")
    private Integer estoqueMinimo;

    @CsvBindByName(column = "NCM")
    private String ncm;

    @CsvBindByName(column = "UNIDADE")
    private String unidade;

    // A empresa NÃO vem do Excel, ela é injetada pelo sistema (Tenant)
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;
}