package com.smartstock.backend.service;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;

    private final ProdutoService produtoService;

    public WebhookService(ProdutoRepository produtoRepository, EmpresaRepository empresaRepository, ProdutoService produtoService) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.produtoService = produtoService;
    }

    @Transactional
    public String processarVendaExterna(VendaExternaDTO dto) {
        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Webhook Erro: Empresa não encontrada."));

        int itensProcessados = 0;

        for (ItemVendaExternaDTO item : dto.getItens()) {
            Produto produto = produtoRepository.findById(item.getProdutoId())
                    .orElseThrow(() -> new RuntimeException("Webhook Erro: Produto não encontrado com ID " + item.getProdutoId()));

            if (!produto.getEmpresa().getId().equals(empresa.getId())) {
                continue;
            }

            try {

                // Isso abate dos lotes que vão vencer primeiro e já gera o Histórico sozinho.
                produtoService.registrarSaida(produto.getId(), item.getQuantidade());
                itensProcessados++;
            } catch (Exception e) {
                System.err.println("Aviso: Falha ao processar item no Webhook: " + e.getMessage());
            }
        }

        return "Webhook recebido! " + itensProcessados + " itens abatidos no estoque usando algoritmo FEFO.";
    }
}