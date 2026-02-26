package com.smartstock.backend.model;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
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

    @CsvBindByName(column = "CATEGORIA")
    private String categoria;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    // (Dono do produto)
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    // LISTA DE LOTES
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)

    private List<Lote> lotes = new ArrayList<>();
    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @PrePersist
    protected void onCreate() {
        dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}