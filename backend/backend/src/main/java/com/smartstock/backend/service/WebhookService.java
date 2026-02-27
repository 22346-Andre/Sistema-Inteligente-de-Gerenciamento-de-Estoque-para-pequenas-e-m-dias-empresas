package com.smartstock.backend.service;

import com.smartstock.backend.dto.ItemVendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WebhookService {

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    public WebhookService(ProdutoRepository produtoRepository, EmpresaRepository empresaRepository, MovimentacaoRepository movimentacaoRepository) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @Transactional
    public String processarVendaExterna(VendaExternaDTO dto) {
        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Webhook Erro: Empresa não encontrada."));

        int itensProcessados = 0;

        for (ItemVendaExternaDTO item : dto.getItens()) {
            Produto produto = produtoRepository.findById(item.getProdutoId())
                    .orElseThrow(() -> new RuntimeException("Webhook Erro: Produto não encontrado com ID " + item.getProdutoId()));

            // Trava de Segurança: Garante que o produto pertence mesmo à empresa do payload
            if (!produto.getEmpresa().getId().equals(empresa.getId())) {
                continue;
            }

            // 1. Abate o estoque em tempo real
            int novaQuantidade = produto.getQuantidade() - item.getQuantidade();
            produto.setQuantidade(novaQuantidade < 0 ? 0 : novaQuantidade); // Evita estoque negativo bizarro
            produtoRepository.save(produto);

            // 2. Registra o histórico automaticamente (Auditoria)
            Movimentacao mov = new Movimentacao();
            mov.setProduto(produto);
            mov.setEmpresa(empresa);
            mov.setTipo(TipoMovimentacao.SAIDA);
            mov.setQuantidade(item.getQuantidade());
            mov.setDataMovimentacao(LocalDateTime.now());
            mov.setObservacao("Venda automática via " + dto.getOrigem() + " | Pedido: " + dto.getIdPedido());

            movimentacaoRepository.save(mov);
            itensProcessados++;
        }

        return "Webhook recebido! " + itensProcessados + " itens abatidos no estoque do cliente.";
    }
}