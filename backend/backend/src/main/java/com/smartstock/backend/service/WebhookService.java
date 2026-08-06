package com.smartstock.backend.service;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaResultadoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Notificacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.NotificacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final ProdutoService produtoService;

    public WebhookService(ProdutoRepository produtoRepository,
                           EmpresaRepository empresaRepository,
                           NotificacaoRepository notificacaoRepository,
                           ProdutoService produtoService) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.produtoService = produtoService;
    }

    // --------------------------------------------------------------------
    // PASSO 1 — Prevenção de IDOR
    // A empresa é resolvida EXCLUSIVAMENTE a partir do segredo do header.
    // Não existe (e não deve voltar a existir) nenhum método que aceite um
    // empresaId vindo de fora pra decidir de qual empresa é a venda.
    // --------------------------------------------------------------------
    public Optional<Empresa> buscarEmpresaPorSegredo(String segredoRecebido) {
        if (segredoRecebido == null || segredoRecebido.isBlank()) {
            return Optional.empty();
        }
        return empresaRepository.findByWebhookSecret(segredoRecebido);
    }

    // --------------------------------------------------------------------
    // PASSO 2 — Falha parcial sem silêncio
    // Cada item é processado numa transação PRÓPRIA (ver
    // processarItemEmTransacaoPropria) — se o item 2 de 3 falhar, isso não
    // desfaz o item 1 que já tinha sido salvo com sucesso. A falha vira uma
    // notificação persistida, não um log que só o desenvolvedor vê.
    // --------------------------------------------------------------------
    public VendaExternaResultadoDTO processarVendaExterna(VendaExternaDTO dto, Empresa empresa) {
        VendaExternaResultadoDTO resultado = new VendaExternaResultadoDTO();

        // Chave única que agrupa todos os itens desta venda externa no
        // histórico de movimentações (limitada a 44 caracteres, mesma regra
        // de antes).
        String chaveVendaExterna = "WEBHOOK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        resultado.setChaveVendaExterna(chaveVendaExterna);

        if (dto.getItens() == null || dto.getItens().isEmpty()) {
            logger.warn("Webhook: payload da empresa {} (id={}) chegou sem itens.", empresa.getNomeFantasia(), empresa.getId());
            return resultado;
        }

        for (ItemVendaExternaDTO item : dto.getItens()) {
            try {
                processarItemEmTransacaoPropria(item, empresa, dto.getOrigem(), chaveVendaExterna);
                resultado.setItensProcessados(resultado.getItensProcessados() + 1);

            } catch (Exception e) {
                // Sem silêncio: além do log técnico (pro desenvolvedor), grava
                // uma notificação PERSISTIDA (pro gestor ver dentro do sistema)
                // e reporta o item na resposta HTTP (pro canal externo, se ele
                // quiser mostrar isso também). A falha de 1 item nunca derruba
                // os outros nem quebra a resposta HTTP.
                String motivoFalha = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

                logger.error("Webhook: falha ao processar item '{}' da venda externa {} (empresa id={}): {}",
                        item.getCodigoBarras(), chaveVendaExterna, empresa.getId(), motivoFalha);

                registrarNotificacaoFalhaParcial(empresa, dto, chaveVendaExterna, item, motivoFalha);
                resultado.adicionarFalha(item.getCodigoBarras(), motivoFalha);
            }
        }

        return resultado;
    }

    // Cada item roda isolado: se o item 2 falhar, isso não desfaz o item 1
    // que já tinha sido salvo com sucesso. A transação de verdade que
    // garante isso é a de ProdutoService.registrarSaidaComEmpresa (chamada
    // logo abaixo) — como é uma chamada entre BEANS diferentes (WebhookService
    // → ProdutoService), passa pelo proxy do Spring normalmente. Não colocamos
    // @Transactional aqui nesta classe porque, se colocássemos, essa
    // chamada seria feita de dentro da MESMA classe (self-invocation) — o
    // Spring simplesmente ignora a anotação nesse caso, então a anotação
    // aqui seria só um comentário disfarçado de proteção real.
    private void processarItemEmTransacaoPropria(ItemVendaExternaDTO item, Empresa empresa, String origem, String chaveVendaExterna) {
        Produto produto = produtoRepository.findByCodigoBarrasAndEmpresaId(item.getCodigoBarras(), empresa.getId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado no seu catálogo (código: " + item.getCodigoBarras() + ")"));

       
        produtoService.registrarSaidaComEmpresa(
                produto.getId(),
                item.getQuantidade(),
                TipoMovimentacao.SAIDA,
                "Venda Externa: " + origem,
                chaveVendaExterna,
                null, //  venda externa via Webhook não tem forma de pagamento capturada no PDV
                empresa.getId()
        );
    }

    private void registrarNotificacaoFalhaParcial(Empresa empresa, VendaExternaDTO dto, String chaveVendaExterna,
                                                    ItemVendaExternaDTO item, String motivoFalha) {
        Notificacao notificacao = new Notificacao();
        notificacao.setEmpresa(empresa);
        notificacao.setTipo("WEBHOOK_FALHA_PARCIAL");
        notificacao.setTitulo("Venda externa com falha parcial (" + dto.getOrigem() + ")");
        notificacao.setMensagem(String.format(
                "A venda externa %s (canal: %s, pedido: %s) teve falha parcial. "
                        + "O produto de código '%s' não foi processado: %s",
                chaveVendaExterna,
                dto.getOrigem() != null ? dto.getOrigem() : "não informado",
                dto.getIdPedido() != null ? dto.getIdPedido() : "não informado",
                item.getCodigoBarras(),
                motivoFalha
        ));
        notificacaoRepository.save(notificacao);
    }
}
