package com.smartstock.backend.integration;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaResultadoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.repository.WebhookItemProcessadoRepository;
import com.smartstock.backend.service.WebhookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookIdempotenciaIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private WebhookItemProcessadoRepository webhookItemProcessadoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Empresa empresa;
    private Produto produto;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setCnpj("11222333000181");
        empresa.setRazaoSocial("Empresa Webhook Teste LTDA");
        empresa = empresaRepository.save(empresa);

        produto = new Produto();
        produto.setNome("Produto Webhook Teste");
        produto.setCodigoBarras("7891000000002");
        produto.setPrecoCusto(new BigDecimal("10.00"));
        produto.setPrecoVenda(new BigDecimal("20.00"));
        produto.setQuantidade(10);
        produto.setEstoqueMinimo(2);
        produto.setEmpresa(empresa);
        produto = produtoRepository.save(produto);
    }

    @AfterEach
    void limparDados() {
        webhookItemProcessadoRepository.deleteAll();
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        usuarioRepository.deleteAll();
        empresaRepository.deleteAll();
    }

    @Test
    void reentregaDoMesmoPedidoNaoDebitaEstoqueDuasVezes() {
        VendaExternaDTO venda = venda();

        VendaExternaResultadoDTO primeiraResposta = webhookService.processarVendaExterna(venda, empresa);
        VendaExternaDTO reentregaComLinhasEmOutraOrdem = venda();
        reentregaComLinhasEmOutraOrdem.setItens(List.of(
                item(produto.getCodigoBarras(), 2),
                item(produto.getCodigoBarras(), 1)));
        VendaExternaResultadoDTO reentrega =
                webhookService.processarVendaExterna(reentregaComLinhasEmOutraOrdem, empresa);

        assertEquals(1, primeiraResposta.getItensProcessados());
        assertEquals(0, primeiraResposta.getItensJaProcessados());
        assertEquals(0, reentrega.getItensProcessados());
        assertEquals(1, reentrega.getItensJaProcessados());
        assertEquals(7, produtoRepository.findById(produto.getId()).orElseThrow().getQuantidade());
        assertEquals(1, webhookItemProcessadoRepository.count());
    }

    @Test
    void entregasConcorrentesDoMesmoPedidoSoDebitamUmaVez() throws Exception {
        VendaExternaDTO venda = venda();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<VendaExternaResultadoDTO> primeira = executor.submit(() -> {
                largada.await();
                return webhookService.processarVendaExterna(venda, empresa);
            });
            Future<VendaExternaResultadoDTO> segunda = executor.submit(() -> {
                largada.await();
                return webhookService.processarVendaExterna(venda, empresa);
            });

            largada.countDown();
            VendaExternaResultadoDTO respostaA = primeira.get();
            VendaExternaResultadoDTO respostaB = segunda.get();

            assertEquals(1, respostaA.getItensProcessados() + respostaB.getItensProcessados());
            assertEquals(1, respostaA.getItensJaProcessados() + respostaB.getItensJaProcessados());
            assertEquals(7, produtoRepository.findById(produto.getId()).orElseThrow().getQuantidade());
            assertEquals(1, webhookItemProcessadoRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private VendaExternaDTO venda() {
        VendaExternaDTO venda = new VendaExternaDTO();
        venda.setOrigem("LOJA_TESTE");
        venda.setIdPedido("PEDIDO-123");
        venda.setItens(List.of(
                item(produto.getCodigoBarras(), 1),
                item(produto.getCodigoBarras(), 2)));
        return venda;
    }

    private ItemVendaExternaDTO item(String codigoBarras, int quantidade) {
        ItemVendaExternaDTO item = new ItemVendaExternaDTO();
        item.setCodigoBarras(codigoBarras);
        item.setQuantidade(quantidade);
        return item;
    }
}
