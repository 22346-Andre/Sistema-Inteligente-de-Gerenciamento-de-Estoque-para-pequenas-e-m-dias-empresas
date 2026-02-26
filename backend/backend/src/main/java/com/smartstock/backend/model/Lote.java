package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Entity
@Table(name = "lotes")
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_lote")
    private String numeroLote;

    // Quantidade DESTE lote específico
    private Integer quantidade;

    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    // A qual produto este lote pertence
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @PrePersist
    protected void onCreate() {
        if (dataEntrada == null) {
            dataEntrada = LocalDateTime.now();
        }
    }
}
