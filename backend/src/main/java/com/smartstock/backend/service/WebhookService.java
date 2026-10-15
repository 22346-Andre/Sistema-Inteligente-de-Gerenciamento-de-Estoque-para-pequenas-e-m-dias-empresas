package com.smartstock.backend.service;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaResultadoDTO;
import com.smartstock.backend.exception.EstoqueInsuficienteException;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Notificacao;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.NotificacaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final EmpresaRepository empresaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final WebhookItemService webhookItemService;

    public WebhookService(EmpresaRepository empresaRepository,
                           NotificacaoRepository notificacaoRepository,
                           WebhookItemService webhookItemService) {
        this.empresaRepository = empresaRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.webhookItemService = webhookItemService;
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
        validarPayload(dto);

        String origem = dto.getOrigem().trim().toUpperCase();
        String pedidoId = dto.getIdPedido().trim();
        String chaveVendaExterna = gerarChaveVendaExterna(empresa.getId(), origem, pedidoId);
        VendaExternaResultadoDTO resultado = new VendaExternaResultadoDTO();
        resultado.setChaveVendaExterna(chaveVendaExterna);

        for (ItemVendaExternaDTO item : agruparItens(dto)) {
            try {
                boolean processadoAgora = processarItemEmTransacaoPropria(
                        item, empresa, origem, pedidoId, chaveVendaExterna);
                if (processadoAgora) {
                    resultado.setItensProcessados(resultado.getItensProcessados() + 1);
                } else {
                    resultado.setItensJaProcessados(resultado.getItensJaProcessados() + 1);
                }

            } catch (DataIntegrityViolationException e) {
                // A restrição única protege também contra duas entregas simultâneas
                // que passaram pela verificação anterior ao INSERT.
                if (webhookItemService.jaProcessado(
                        empresa.getId(), origem, pedidoId, item.getCodigoBarras().trim())) {
                    resultado.setItensJaProcessados(resultado.getItensJaProcessados() + 1);
                    continue;
                }
                registrarFalha(dto, empresa, item, chaveVendaExterna, e, resultado);
            } catch (Exception e) {
                registrarFalha(dto, empresa, item, chaveVendaExterna, e, resultado);
            }
        }

        return resultado;
    }

    private java.util.List<ItemVendaExternaDTO> agruparItens(VendaExternaDTO dto) {
        Map<String, Integer> quantidadesPorCodigo = new LinkedHashMap<>();
        for (ItemVendaExternaDTO item : dto.getItens()) {
            String codigo = item.getCodigoBarras().trim();
            try {
                quantidadesPorCodigo.merge(codigo, item.getQuantidade(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("A quantidade total de um produto excede o limite permitido.", e);
            }
        }

        return quantidadesPorCodigo.entrySet().stream().map(entrada -> {
            ItemVendaExternaDTO item = new ItemVendaExternaDTO();
            item.setCodigoBarras(entrada.getKey());
            item.setQuantidade(entrada.getValue());
            return item;
        }).toList();
    }

    private void registrarFalha(VendaExternaDTO dto, Empresa empresa, ItemVendaExternaDTO item,
                                String chaveVendaExterna, Exception erro, VendaExternaResultadoDTO resultado) {
        String motivoFalha = erro instanceof RecursoNaoEncontradoException
                || erro instanceof EstoqueInsuficienteException
                || erro instanceof RegraNegocioException
                || erro instanceof IllegalArgumentException
                || erro instanceof IllegalStateException
                ? erro.getMessage()
                : "Falha interna ao processar o item. Tente novamente.";

        logger.error("Webhook: falha ao processar item '{}' da venda externa {} (empresa id={})",
                item.getCodigoBarras(), chaveVendaExterna, empresa.getId(), erro);

        registrarNotificacaoFalhaParcial(empresa, dto, chaveVendaExterna, item, motivoFalha);
        resultado.adicionarFalha(item.getCodigoBarras(), motivoFalha);
    }

    private void validarPayload(VendaExternaDTO dto) {
        if (dto == null || dto.getOrigem() == null || dto.getOrigem().isBlank()
                || dto.getIdPedido() == null || dto.getIdPedido().isBlank()
                || dto.getItens() == null || dto.getItens().isEmpty()) {
            throw new IllegalArgumentException("Origem, ID do pedido e ao menos um item são obrigatórios.");
        }

        for (ItemVendaExternaDTO item : dto.getItens()) {
            if (item == null || item.getCodigoBarras() == null || item.getCodigoBarras().isBlank()
                    || item.getQuantidade() == null || item.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Cada item deve informar código de barras e quantidade maior que zero.");
            }
        }
    }

    private boolean processarItemEmTransacaoPropria(ItemVendaExternaDTO item, Empresa empresa,
                                                     String origem, String pedidoId,
                                                     String chaveVendaExterna) {
        return webhookItemService.processar(empresa, origem, pedidoId,
                item.getCodigoBarras().trim(), item.getQuantidade(), chaveVendaExterna);
    }

    private String gerarChaveVendaExterna(Long empresaId, String origem, String pedidoId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (empresaId + "|" + origem + "|" + pedidoId).getBytes(StandardCharsets.UTF_8));
            return "WEBHOOK-" + HexFormat.of().formatHex(digest).substring(0, 32).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível para gerar a chave do pedido.", e);
        }
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
