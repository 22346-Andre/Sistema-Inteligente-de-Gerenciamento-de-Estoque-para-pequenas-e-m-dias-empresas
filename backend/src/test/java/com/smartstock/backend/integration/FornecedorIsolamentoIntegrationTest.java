package com.smartstock.backend.integration;

import com.smartstock.backend.dto.DespesaDTO;
import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.repository.DespesaRepository;
import com.smartstock.backend.service.DespesaService;
import com.smartstock.backend.service.ProdutoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Um fornecedor só pode ser vinculado a produto/despesa se for da mesma empresa.
 * Antes, ProdutoService.salvar e DespesaService.registrar/atualizar aceitavam
 * qualquer fornecedorId existente, de qualquer empresa (só ProdutoService.atualizar
 * checava isso).
 */
class FornecedorIsolamentoIntegrationTest extends IntegrationTestBase {

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private DespesaRepository despesaRepository;
    @Autowired private ProdutoService produtoService;
    @Autowired private DespesaService despesaService;

    private Empresa empresaA;
    private Empresa empresaB;
    private Fornecedor fornecedorDaEmpresaB;

    @BeforeEach
    void setUp() {
        empresaA = criarEmpresa("11222333000181", "Empresa A LTDA");
        empresaB = criarEmpresa("99888777000162", "Empresa B LTDA");

        fornecedorDaEmpresaB = new Fornecedor();
        fornecedorDaEmpresaB.setNome("Fornecedor da Empresa B");
        fornecedorDaEmpresaB.setCnpj("22333444000155");
        fornecedorDaEmpresaB.setEmpresa(empresaB);
        fornecedorDaEmpresaB = fornecedorRepository.save(fornecedorDaEmpresaB);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        despesaRepository.deleteAll();
        produtoRepository.deleteAll();
        fornecedorRepository.deleteAll();
        empresaRepository.deleteAll();
    }

    @Test
    void naoVinculaFornecedorDeOutraEmpresaAoCriarProduto() {
        autenticarComo(empresaA.getId());

        ProdutoDTO dto = new ProdutoDTO();
        dto.setNome("Produto Teste");
        dto.setCodigoBarras("7891000000123");
        dto.setPrecoCusto(new BigDecimal("5.00"));
        dto.setPrecoVenda(new BigDecimal("10.00"));
        dto.setQuantidade(1);
        setFornecedorId(dto, fornecedorDaEmpresaB.getId());

        assertThrows(RecursoNaoEncontradoException.class, () -> produtoService.salvar(dto));
    }

    @Test
    void naoVinculaFornecedorDeOutraEmpresaAoRegistrarDespesa() {
        DespesaDTO dto = new DespesaDTO();
        dto.setDescricao("Compra de material");
        dto.setValor(new BigDecimal("100.00"));
        dto.setDataVencimento(LocalDate.now().plusDays(30));
        dto.setFornecedorId(fornecedorDaEmpresaB.getId());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> despesaService.registrar(empresaA.getId(), dto));
    }

    @Test
    void empresaDonaContinuaConseguindoVincularOProprioFornecedor() {
        autenticarComo(empresaB.getId());

        ProdutoDTO dto = new ProdutoDTO();
        dto.setNome("Produto da Empresa B");
        dto.setCodigoBarras("7891000000456");
        dto.setPrecoCusto(new BigDecimal("5.00"));
        dto.setPrecoVenda(new BigDecimal("10.00"));
        dto.setQuantidade(1);
        setFornecedorId(dto, fornecedorDaEmpresaB.getId());

        assertTrue(produtoService.salvar(dto).getId() != null);
    }

    private void setFornecedorId(ProdutoDTO dto, Long id) {
        // ProdutoDTO tem o campo, mas o setter usado no fluxo real de "criar produto"
        // é este (o mesmo caminho que o ProdutoController expõe).
        dto.setFornecedorId(id);
    }

    private Empresa criarEmpresa(String cnpj, String razaoSocial) {
        Empresa e = new Empresa();
        e.setCnpj(cnpj);
        e.setRazaoSocial(razaoSocial);
        return empresaRepository.save(e);
    }

    private void autenticarComo(Long empresaId) {
        Jwt jwt = Jwt.withTokenValue("token-fake")
                .header("alg", "none")
                .claim("empresaId", empresaId)
                .claim("perfil", "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    }
}
