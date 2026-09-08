package com.smartstock.backend.integration;

import com.smartstock.backend.dto.MovimentacaoPdvDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.service.MovimentacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.smartstock.backend.repository.MovimentacaoRepository;
import org.junit.jupiter.api.AfterEach;
/**
 * Cobre o fluxo mais sensível a dinheiro do sistema: uma venda errada no
 * PDV significa estoque incorreto (produto "some" ou vende negativo) e,
 * em último caso, dinheiro saindo do caixa sem produto de verdade.
 */
class PdvFluxoIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MovimentacaoService movimentacaoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    private Empresa empresa;
    private Produto produto;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setCnpj("11222333000181");
        empresa.setRazaoSocial("Empresa PDV Teste LTDA");
        empresa = empresaRepository.save(empresa);

        produto = new Produto();
        produto.setNome("Produto Teste PDV");
        produto.setCodigoBarras("7891000000001");
        produto.setPrecoCusto(new BigDecimal("10.00"));
        produto.setPrecoVenda(new BigDecimal("20.00"));
        produto.setQuantidade(10);
        produto.setEstoqueMinimo(2);
        produto.setEmpresa(empresa);
        produto = produtoRepository.save(produto);

        autenticarComo(empresa.getId());
    }

    @AfterEach
    void limparDados() {
    movimentacaoRepository.deleteAll();
    usuarioRepository.deleteAll();
    produtoRepository.deleteAll();
    empresaRepository.deleteAll();
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
    void saidaValidaDeveDebitarEstoqueCorretamente() {
        MovimentacaoPdvDTO dto = new MovimentacaoPdvDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setTipo("SAIDA");
        dto.setQuantidade(3);

        movimentacaoService.registrarViaPDV(dto);

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(7, atualizado.getQuantidade(), "Deveria ter debitado 3 unidades das 10 iniciais");
    }

    @Test
    void entradaDeveCreditarEstoque() {
        MovimentacaoPdvDTO dto = new MovimentacaoPdvDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setTipo("ENTRADA");
        dto.setQuantidade(5);

        movimentacaoService.registrarViaPDV(dto);

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(15, atualizado.getQuantidade());
    }

    @Test
    void naoDeveVenderMaisDoQueTemEmEstoque() {
        MovimentacaoPdvDTO dto = new MovimentacaoPdvDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setTipo("SAIDA");
        dto.setQuantidade(999); // estoque tem só 10

        assertThrows(RuntimeException.class, () -> movimentacaoService.registrarViaPDV(dto));

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(10, atualizado.getQuantidade(), "Estoque não pode ter sido alterado numa venda rejeitada");
    }

    /**
     * O caso que mais importa: dois caixas vendendo o MESMO produto ao
     * mesmo tempo não podem, juntos, vender mais do que existe fisicamente.
     * É exatamente pra isso que existe o lock pessimista em
     * buscarComLockParaAtualizacao -- este teste garante que ele realmente
     * segura a concorrência, e não só existe no código sem efeito.
     */
    @Test
    void vendasConcorrentesNaoPodemVenderMaisDoQueOEstoqueTem() throws InterruptedException {
        int tentativasSimultaneas = 20; // 20 caixas tentando vender 1 unidade cada, só 10 em estoque
        ExecutorService executor = Executors.newFixedThreadPool(tentativasSimultaneas);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(tentativasSimultaneas);
        AtomicInteger sucessos = new AtomicInteger(0);
        Long empresaId = empresa.getId();

        for (int i = 0; i < tentativasSimultaneas; i++) {
            executor.submit(() -> {
                try {
                    largada.await();
                    autenticarComo(empresaId); // SecurityContext é por-thread
                    MovimentacaoPdvDTO dto = new MovimentacaoPdvDTO();
                    dto.setCodigoBarras(produto.getCodigoBarras());
                    dto.setTipo("SAIDA");
                    dto.setQuantidade(1);
                    movimentacaoService.registrarViaPDV(dto);
                    sucessos.incrementAndGet();
                } catch (Exception ignorado) {
                    // Esperado pra quem chegar depois do estoque zerar
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        assertTrue(chegada.await(30, TimeUnit.SECONDS), "Todas as vendas deveriam terminar dentro do timeout");
        executor.shutdown();

        assertEquals(10, sucessos.get(), "Só 10 das 20 tentativas podem ter tido sucesso (estoque inicial era 10)");

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, atualizado.getQuantidade(), "Estoque final tem que ser exatamente 0, nunca negativo");
    }
}
