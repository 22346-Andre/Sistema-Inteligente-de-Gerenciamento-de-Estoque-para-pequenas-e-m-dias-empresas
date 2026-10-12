package com.smartstock.backend.infra.security;

/**
 * Expressões de autorização reutilizáveis nos @PreAuthorize dos controllers.
 *
 * Matriz por perfil:
 *  - ADMIN / SUPER_ADMIN: tudo da própria empresa.
 *  - ESTOQUISTA: cadastro e edição de produtos, lotes/entradas, fornecedores e importação.
 *  - CAIXA: operação de venda (PDV/scanner, fiados, Pix, sessão de caixa) e consultas.
 *
 * Exclusões (produto, fornecedor) e relatórios financeiros ficam só com ADMIN.
 * O perfil vem do claim "perfil" do JWT (ver SecurityConfigurations).
 */
public final class Permissoes {

    /** Somente o administrador da empresa (ou o suporte). */
    public static final String SOMENTE_ADMIN = "hasAnyAuthority('ADMIN', 'SUPER_ADMIN')";

    /** Administrador ou estoquista: gestão de produtos, entradas, fornecedores e importação. */
    public static final String ADMIN_OU_ESTOQUISTA = "hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'ESTOQUISTA')";

    private Permissoes() {
        // classe utilitária — não instanciável
    }
}