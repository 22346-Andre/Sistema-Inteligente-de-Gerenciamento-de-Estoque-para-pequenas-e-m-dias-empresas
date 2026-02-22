package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cnpj; // Bloqueia CNPJ duplicado direto no banco

    @Column(nullable = false)
    private String razaoSocial;

    private String nomeFantasia;

    @Column(name = "email_contato")
    private String emailContato;
}
