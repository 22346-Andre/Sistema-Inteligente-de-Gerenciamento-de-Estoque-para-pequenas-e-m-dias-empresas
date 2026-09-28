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

    // Credencial de acesso ao webhook de vendas. WRITE_ONLY: nunca sai em nenhum
    // JSON (ex.: /usuarios/me, /empresas/minha-empresa, listagem de usuários).
    // Quem precisa ver o segredo (ADMIN) usa GET /empresas/minha-empresa/webhook-secret.
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Column(name = "webhook_secret", unique = true)
    private String webhookSecret;

   
    @Column(name = "chave_pix")
    private String chavePix;

    // Capital Social — preenchido manualmente pelo dono do negócio, o
    // sistema não tem como descobrir isso sozinho. Usado no Balanço
    // Patrimonial (compõe o Patrimônio Líquido, junto com Lucros
    // Acumulados, que esse sim é calculado a partir do histórico de vendas).
    @Column(name = "capital_social")
    private java.math.BigDecimal capitalSocial;

    @PrePersist
    protected void gerarWebhookSecret() {
        if (this.webhookSecret == null) {
            this.webhookSecret = java.util.UUID.randomUUID().toString();
        }
    }
}