package com.smartstock.backend.integration;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.service.DashboardService;
import com.smartstock.backend.service.MovimentacaoService;
import com.smartstock.backend.dto.MovimentacaoPdvDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.smartstock.backend.repository.MovimentacaoRepository;
import org.junit.jupiter.api.AfterEach;
/**
 * O bug mais caro que existe num sistema multi-tenant: a Empresa A
 * enxergando ou alterando dado da Empresa B. Cada teste aqui monta duas
 * empresas concorrentes de propósito (mesmo código de barras, catálogos
 * parecidos) exatamente pra forçar o cenário onde um WHERE empresa_id
 * esquecido em algum repositório/query passaria despercebido em dados
 * "de brinquedo" mas apareceria aqui.
 */
class TenantIsolationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private MovimentacaoService movimentacaoService;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    private Empresa empresaA;
    private Empresa empresaB;

    @BeforeEach
    void setUp() {
        empresaA = criarEmpresa("11222333000181", "Empresa A LTDA");
        empresaB = criarEmpresa("99888777000162", "Empresa B LTDA");

        // Mesmo código de barras nas duas empresas -- só é permitido porque
        // a unique constraint é (codigo_barras, empresa_id), não
        // codigo_barras sozinho. Isso é justamente o caso mais fácil de
        // vazar se algum filtro por empresa_id faltar em algum lugar.
        criarProduto(empresaA, "7891000000001", "Produto da Empresa A", 10);
        criarProduto(empresaB, "7891000000001", "Produto da Empresa B", 50);
    }

    @AfterEach
    void limparDados() {
    movimentacaoRepository.deleteAll();
    produtoRepository.deleteAll();
    empresaRepository.deleteAll();
}

    private Empresa criarEmpresa(String cnpj, String razaoSocial) {
        Empresa empresa = new Empresa();
        empresa.setCnpj(cnpj);
        empresa.setRazaoSocial(razaoSocial);
        return empresaRepository.save(empresa);
    }

    private Produto criarProduto(Empresa empresa, String codigoBarras, String nome, int quantidade) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setCodigoBarras(codigoBarras);
        produto.setPrecoCusto(new BigDecimal("10.00"));
        produto.setPrecoVenda(new BigDecimal("20.00"));
        produto.setQuantidade(quantidade);
        produto.setEstoqueMinimo(2);
        produto.setEmpresa(empresa);
        return produtoRepository.save(produto);
    }

    private void autenticarComo(Long empresaId) {
        Jwt jwt = Jwt.withTokenValue("token-fake")
                .header("alg", "none")
                .claim("empresaId", empresaId)
                .claim("perfil", "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt, null));
    }

    @Test
    void buscaPorCodigoBarrasNuncaRetornaProdutoDeOutraEmpresa() {
        Optional<Produto> encontrado = produtoRepository
                .findByCodigoBarrasAndEmpresaId("7891000000001", empresaA.getId());

        assertTrue(encontrado.isPresent());
        assertEquals("Produto da Empresa A", encontrado.get().getNome());
        assertEquals(empresaA.getId(), encontrado.get().getEmpresa().getId());
    }

    @Test
    void listaDeProdutosDeUmaEmpresaNaoContemProdutosDaOutra() {
        List<Produto> produtosDaA = produtoRepository.findByEmpresaId(empresaA.getId());

        assertEquals(1, produtosDaA.size());
        assertTrue(produtosDaA.stream().allMatch(p -> p.getEmpresa().getId().equals(empresaA.getId())));
    }

    @Test
    void dashboardDaEmpresaLogadaNuncaMostraContadorDaOutraEmpresa() {
        // Empresa B tem 50 no estoque do produto dela, Empresa A tem 10 --
        // se o dashboard vazar dado entre empresas, esse número bate errado.
        autenticarComo(empresaA.getId());
        var resumoA = dashboardService.obterResumoDashboard();
        assertEquals(1L, resumoA.getTotalProdutos(), "Dashboard da Empresa A só pode contar o 1 produto dela");

        autenticarComo(empresaB.getId());
        var resumoB = dashboardService.obterResumoDashboard();
        assertEquals(1L, resumoB.getTotalProdutos(), "Dashboard da Empresa B só pode contar o 1 produto dela");
    }

    @Test
    void vendaNoPdvDaEmpresaANuncaAlteraEstoqueDaEmpresaB() {
        autenticarComo(empresaA.getId());

        MovimentacaoPdvDTO dto = new MovimentacaoPdvDTO();
        dto.setCodigoBarras("7891000000001"); // código repetido nas duas empresas de propósito
        dto.setTipo("SAIDA");
        dto.setQuantidade(3);
        movimentacaoService.registrarViaPDV(dto);

        Produto produtoA = produtoRepository.findByCodigoBarrasAndEmpresaId("7891000000001", empresaA.getId()).orElseThrow();
        Produto produtoB = produtoRepository.findByCodigoBarrasAndEmpresaId("7891000000001", empresaB.getId()).orElseThrow();

        assertEquals(7, produtoA.getQuantidade(), "Venda deveria ter debitado só o estoque da Empresa A (10 -> 7)");
        assertEquals(50, produtoB.getQuantidade(), "Estoque da Empresa B tem que continuar intacto em 50");
    }
}
