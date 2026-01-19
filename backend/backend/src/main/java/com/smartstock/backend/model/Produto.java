package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data // O Lombok cria Getters, Setters e toString sozinho
@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String descricao;
    private BigDecimal preco;
    private Integer quantidade;

    @Column(name = "estoque_minimo")
    private Integer estoqueMinimo;
}