package com.smartstock.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registra cada NFe (XML da SEFAZ) já importada, para impedir que o mesmo
 * arquivo seja processado duas vezes e duplique o estoque.
 *
 * A "chave de acesso" é o identificador único de 44 dígitos de uma NFe
 * (atributo Id da tag <infNFe>, com o prefixo "NFe" removido). Ela já
 * incorpora CNPJ do emitente, número, série e modelo da nota, então é
 * garantidamente única a nível nacional — não é uma chave que o sistema
 * inventa, é a mesma que a Receita/SEFAZ usa para identificar a nota.
 */
@Data
@Entity
@Table(name = "notas_fiscais_importadas", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chave_acesso", "empresa_id"})
})
public class NotaFiscalImportada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_acesso", nullable = false, length = 44)
    private String chaveAcesso;

    @Column(name = "numero_nota")
    private String numeroNota;

    private String serie;

    @Column(name = "data_emissao")
    private String dataEmissao;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @Column(name = "quantidade_itens")
    private Integer quantidadeItens;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "nome_arquivo")
    private String nomeArquivo;

    @Column(name = "data_importacao", updatable = false)
    private LocalDateTime dataImportacao;

    @PrePersist
    protected void onCreate() {
        if (dataImportacao == null) {
            dataImportacao = LocalDateTime.now();
        }
    }
}