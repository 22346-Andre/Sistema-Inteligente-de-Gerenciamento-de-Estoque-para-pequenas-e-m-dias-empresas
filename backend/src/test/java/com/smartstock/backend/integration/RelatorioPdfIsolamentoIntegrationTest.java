package com.smartstock.backend.integration;

import com.smartstock.backend.dto.MovimentacaoPdvDTO;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.service.MovimentacaoService;
import com.smartstock.backend.service.RelatorioPdfService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que DANFE e cupom (por id e por chave de nota) só saem para a
 * empresa dona da movimentação. Antes, /relatorios/danfe/{id}/pdf e
 * /relatorios/cupom/{id}/pdf faziam findById sem filtro de empresa, e as
 * rotas /lote/{chave} filtravam só pela chave da nota.
 */
class RelatorioPdfIsolamentoIntegrationTest extends IntegrationTestBase {

    private static final String CHAVE_NOTA = "35260311222333000181550010000000011000000011";

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentacaoRepository movimentacaoRepository;
    @Autowired private MovimentacaoService movimentacaoService;
    @Autowired private RelatorioPdfService relatorioPdfService;

    private Empresa empresaA;
    private Empresa empresaB;
    private Movimentacao vendaDaEmpresaB;

    @BeforeEach
    void setUp() {
        empresaA = criarEmpresa("11222333000181", "Empresa A LTDA");
        empresaB = criarEmpresa("99888777000162", "Empresa B LTDA");
        criarProduto(empresaA, "7891000000009", "Produto A");
        criarProduto(empresaB, "7891000000009", "Produto B");

        autenticarComo(empresaB.getId());
        MovimentacaoPdvDTO dto = new MovimentacaoPdvDTO();
        dto.setCodigoBarras("7891000000009");
        dto.setTipo("SAIDA");
        dto.setQuantidade(1);
        vendaDaEmpresaB = movimentacaoService.registrarViaPDV(dto);
        vendaDaEmpresaB.setChaveNotaFiscal(CHAVE_NOTA);
        vendaDaEmpresaB = movimentacaoRepository.save(vendaDaEmpresaB);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        empresaRepository.deleteAll();
    }

    @Test
    void cupomPorIdDeOutraEmpresaNaoEhEntregue() {
        autenticarComo(empresaA.getId());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> relatorioPdfService.gerarCupomFiscalPdf(vendaDaEmpresaB.getId()));
    }

    @Test
    void danfePorIdDeOutraEmpresaNaoEhEntregue() {
        autenticarComo(empresaA.getId());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> relatorioPdfService.gerarDanfeSimplesPdf(vendaDaEmpresaB.getId()));
    }

    @Test
    void cupomEDanfePorChaveDeOutraEmpresaNaoSaem() {
        autenticarComo(empresaA.getId());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> relatorioPdfService.gerarCupomLotePdf(CHAVE_NOTA));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> relatorioPdfService.gerarDanfeLotePdf(CHAVE_NOTA));
    }

    @Test
    void consultaPorChaveSoRetornaMovimentacaoDaPropriaEmpresa() {
        assertTrue(movimentacaoRepository
                .findByChaveNotaFiscalAndEmpresaId(CHAVE_NOTA, empresaA.getId()).isEmpty());
        assertEquals(1, movimentacaoRepository
                .findByChaveNotaFiscalAndEmpresaId(CHAVE_NOTA, empresaB.getId()).size());
    }

    @Test
    void empresaDonaContinuaConseguindoGerarOProprioCupom() {
        autenticarComo(empresaB.getId());
        byte[] pdf = relatorioPdfService.gerarCupomFiscalPdf(vendaDaEmpresaB.getId());
        assertTrue(pdf.length > 0);
    }

    private Empresa criarEmpresa(String cnpj, String razaoSocial) {
        Empresa e = new Empresa();
        e.setCnpj(cnpj);
        e.setRazaoSocial(razaoSocial);
        return empresaRepository.save(e);
    }

    private void criarProduto(Empresa empresa, String codigoBarras, String nome) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setCodigoBarras(codigoBarras);
        p.setPrecoCusto(new BigDecimal("10.00"));
        p.setPrecoVenda(new BigDecimal("20.00"));
        p.setQuantidade(10);
        p.setEstoqueMinimo(2);
        p.setEmpresa(empresa);
        produtoRepository.save(p);
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
