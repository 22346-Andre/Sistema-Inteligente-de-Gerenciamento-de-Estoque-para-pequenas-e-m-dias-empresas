package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
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

    // --- CAMPOS PARA O RELATÓRIO FISCAL ---
    private String ncm;
    private String unidade;

    // O VÍNCULO COM A EMPRESA ---
    @ManyToOne
    @JoinColumn(name = "empresa_id") // Cria a coluna empresa_id no banco
    private Empresa empresa;
}