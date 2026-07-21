package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

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

    private String telefone;
    private String endereco;
    private String cidade;
    private String estado;


    private LocalDateTime ultimoAcesso = LocalDateTime.now(); // Já começa com a data de hoje ao criar

 
    @Column(name = "dias_estoque_morto")
    private Integer diasParaEstoqueMorto = 90;

   
    @Column(name = "webhook_secret", unique = true)
    private String webhookSecret;

    @PrePersist
    protected void gerarWebhookSecret() {
        if (this.webhookSecret == null) {
            this.webhookSecret = java.util.UUID.randomUUID().toString();
        }
    }
}