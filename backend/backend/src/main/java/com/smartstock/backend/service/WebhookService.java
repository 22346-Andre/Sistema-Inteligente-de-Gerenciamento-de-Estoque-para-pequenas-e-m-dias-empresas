package com.smartstock.backend.service;

import com.smartstock.backend.exception.RecursoNaoEncontradoException;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;
    private final ProdutoService produtoService;

    public WebhookService(ProdutoRepository produtoRepository, EmpresaRepository empresaRepository, ProdutoService produtoService) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.produtoService = produtoService;
    }

    
    public boolean segredoValidoParaEmpresa(Long empresaId, String segredoRecebido) {
        if (empresaId == null || segredoRecebido == null) return false;

        return empresaRepository.findById(empresaId)
                .map(Empresa::getWebhookSecret)
                .filter(segredoReal -> segredoReal != null && segredoReal.equals(segredoRecebido))
                .isPresent();
    }

    @Transactional
    public String processarVendaExterna(VendaExternaDTO dto) {
        // 1. Valida a empresa (Tenant SaaS)
        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Webhook Erro: Empresa não encontrada."));

        int itensProcessados = 0;

        //  Gera uma Chave de Transação única para agrupar todos os itens desta venda externa
        // Usamos UUID para garantir que a chave é única e a convertemos para não ultrapassar 44 caracteres
        String chaveVendaExterna = "WEBHOOK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

        // 2. Passa por cada item que foi vendido na loja externa
        for (ItemVendaExternaDTO item : dto.getItens()) {

            // Busca o produto usando o Código de Barras e a Empresa
            Optional<Produto> produtoOpt = produtoRepository.findByCodigoBarrasAndEmpresaId(item.getCodigoBarras(), empresa.getId());

            // Se o vendedor cadastrou na Shopee/ML um produto que não existe no SmartStock, o sistema avisa mas não trava o resto.
            if (produtoOpt.isEmpty()) {
                logger.warn("Webhook: produto com código '{}' não encontrado na empresa {} (id={})",
                        item.getCodigoBarras(), empresa.getNomeFantasia(), empresa.getId());
                continue;
            }

            Produto produto = produtoOpt.get();

            try {
                produtoService.registrarSaida(
                        produto.getId(),
                        item.getQuantidade(),
                        TipoMovimentacao.SAIDA,
                        "Venda Externa: " + dto.getOrigem(),
                        chaveVendaExterna
                );
                itensProcessados++;
            } catch (Exception e) {
                logger.error("Webhook: falha ao baixar estoque do item '{}' (empresa id={}): {}",
                        item.getCodigoBarras(), empresa.getId(), e.getMessage());
            }
        }

        return String.format("Webhook da %s recebido! %d itens abatidos no estoque sob a chave %s usando algoritmo FEFO.",
                dto.getOrigem(), itensProcessados, chaveVendaExterna);
    }
}