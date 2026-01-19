package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true) // Email único
    private String email;

    @Column(nullable = false)
    private String senha;

    private String perfil; // Ex: "ADMIN" ou "FUNCIONARIO"
}
