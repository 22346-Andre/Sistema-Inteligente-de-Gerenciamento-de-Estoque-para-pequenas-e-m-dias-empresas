package com.smartstock.backend.service;

import com.smartstock.backend.dto.MovimentacaoDTO;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Importante para garantir segurança nos dados

import java.util.List;

@Service
public class MovimentacaoService {

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional // Garante que se der erro, ele desfaz tudo (não salva movimentação pela metade)
    public Movimentacao registrar(MovimentacaoDTO dto) {
        // 1. Buscar o produto (se não existir, erro)
        Produto produto = produtoRepository.findById(dto.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado!"));

        // 2. Calcular o novo saldo
        if (dto.getTipo() == TipoMovimentacao.ENTRADA) {
            // Entrada: Soma
            produto.setQuantidade(produto.getQuantidade() + dto.getQuantidade());
        } else if (dto.getTipo() == TipoMovimentacao.SAIDA) {
            // Saída: Valida se tem estoque e Diminui
            if (produto.getQuantidade() < dto.getQuantidade()) {
                throw new RuntimeException("Estoque insuficiente! Disponível: " + produto.getQuantidade());
            }
            produto.setQuantidade(produto.getQuantidade() - dto.getQuantidade());
        }

        // 3. Salvar o novo saldo do Produto
        produtoRepository.save(produto);

        // 4. Salvar o histórico da Movimentação
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setProduto(produto);
        movimentacao.setTipo(dto.getTipo());
        movimentacao.setQuantidade(dto.getQuantidade());

        return movimentacaoRepository.save(movimentacao);
    }

    public List<Movimentacao> listarTodas() {
        return movimentacaoRepository.findAll();
    }
}
