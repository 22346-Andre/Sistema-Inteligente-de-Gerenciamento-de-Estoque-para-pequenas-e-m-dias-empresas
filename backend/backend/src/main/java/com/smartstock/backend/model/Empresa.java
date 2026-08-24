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

    // Data em que o e-mail de aviso de inatividade ("sua conta vai ser
    // apagada em 30 dias") foi enviado — null enquanto nenhum aviso foi
    // disparado. Ver CleanService: usado pra não reenviar o aviso todo dia
    // e pra saber quando os 30 dias de carência terminaram.
    @Column(name = "aviso_inatividade_enviado_em")
    private LocalDateTime avisoInatividadeEnviadoEm;

 
    @Column(name = "dias_estoque_morto")
    private Integer diasParaEstoqueMorto = 90;

   
    @Column(name = "webhook_secret", unique = true)
    private String webhookSecret;

   
    @Column(name = "chave_pix")
    private String chavePix;

    @PrePersist
    protected void gerarWebhookSecret() {
        if (this.webhookSecret == null) {
            this.webhookSecret = java.util.UUID.randomUUID().toString();
        }
    }
}