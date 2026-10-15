package com.smartstock.backend.service;

import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.model.WebhookItemProcessado;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.repository.WebhookItemProcessadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WebhookItemService {

    private final ProdutoRepository produtoRepository;
    private final WebhookItemProcessadoRepository processadoRepository;
    private final ProdutoService produtoService;

    public WebhookItemService(ProdutoRepository produtoRepository,
                              WebhookItemProcessadoRepository processadoRepository,
                              ProdutoService produtoService) {
        this.produtoRepository = produtoRepository;
        this.processadoRepository = processadoRepository;
        this.produtoService = produtoService;
    }

    @Transactional
    public boolean processar(Empresa empresa, String origem, String pedidoId,
                            String codigoBarras, int quantidade, String chaveMovimentacao) {
        if (processadoRepository.existsByEmpresaIdAndOrigemAndPedidoIdAndCodigoBarras(
                empresa.getId(), origem, pedidoId, codigoBarras)) {
            return false;
        }

        var produto = produtoRepository.findByCodigoBarrasAndEmpresaId(codigoBarras, empresa.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto não encontrado no catálogo para o código informado."));

        WebhookItemProcessado registro = new WebhookItemProcessado();
        registro.setEmpresaId(empresa.getId());
        registro.setOrigem(origem);
        registro.setPedidoId(pedidoId);
        registro.setCodigoBarras(codigoBarras);
        registro.setQuantidade(quantidade);
        registro.setChaveMovimentacao(chaveMovimentacao);
        registro.setProcessadoEm(LocalDateTime.now());
        processadoRepository.saveAndFlush(registro);

        produtoService.registrarSaidaComEmpresa(
                produto.getId(),
                quantidade,
                TipoMovimentacao.SAIDA,
                "Venda Externa: " + origem,
                chaveMovimentacao,
                null,
                empresa.getId()
        );

        return true;
    }

    @Transactional(readOnly = true)
    public boolean jaProcessado(Long empresaId, String origem, String pedidoId, String codigoBarras) {
        return processadoRepository.existsByEmpresaIdAndOrigemAndPedidoIdAndCodigoBarras(
                empresaId, origem, pedidoId, codigoBarras);
    }
}
