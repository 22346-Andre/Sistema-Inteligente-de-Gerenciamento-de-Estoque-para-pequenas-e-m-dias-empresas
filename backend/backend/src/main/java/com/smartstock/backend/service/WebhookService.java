package com.smartstock.backend.service;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
        // 1. Valida a empresa (Tenant SaaS)
        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Webhook Erro: Empresa não encontrada."));

        int itensProcessados = 0;

        // 2. Passa por cada item que foi vendido na loja externa
        for (ItemVendaExternaDTO item : dto.getItens()) {

            //  Busca o produto usando o Código de Barras e a Empresa
            Optional<Produto> produtoOpt = produtoRepository.findByCodigoBarrasAndEmpresaId(item.getCodigoBarras(), empresa.getId());

            // Se o vendedor cadastrou na Shopee um produto que não existe no SmartStock, o sistema avisa mas não trava o resto.
            if (produtoOpt.isEmpty()) {

                System.err.println("Aviso Webhook: Produto com Código '" + item.getCodigoBarras() + "' não encontrado na empresa " + empresa.getNomeFantasia());
                continue;
            }

            Produto produto = produtoOpt.get();

            try {
                // Dá a baixa inteligente (FEFO) nos lotes que vão vencer primeiro
                produtoService.registrarSaida(produto.getId(), item.getQuantidade());
                itensProcessados++;
            } catch (Exception e) {
                System.err.println("Aviso Webhook: Falha ao baixar estoque do item " + item.getCodigoBarras() + " -> " + e.getMessage());
            }
        }

        return String.format("Webhook da %s recebido! %d itens abatidos no estoque usando algoritmo FEFO.", dto.getOrigem(), itensProcessados);
    }
}