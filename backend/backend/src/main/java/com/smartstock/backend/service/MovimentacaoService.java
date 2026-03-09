package com.smartstock.backend.service;

import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import com.smartstock.backend.dto.MovimentacaoPdvDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MovimentacaoService {

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;


    @Autowired
    private ProdutoRepository produtoRepository;

    // --- MÉTODO AUXILIAR DO JWT ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Movimentacao> listarTodas() {
        // Retorna só o histórico da empresa logada para montar o Dashboard!
        return movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(getEmpresaIdLogada());
    }

    public List<Movimentacao> listarPorProduto(Long produtoId) {
        // Busca todas as movimentações do produto específico
        return movimentacaoRepository.findByProdutoIdOrderByDataMovimentacaoDesc(produtoId);
    }

    @Transactional
    public Movimentacao registrarViaPDV(MovimentacaoPdvDTO dto) {
        Long empresaId = getEmpresaIdLogada(); // Usa a sua função segura do JWT

        // 1. Procura o produto pelo código de barras e garante que é da empresa logada
        Produto produto = produtoRepository.findByCodigoBarrasAndEmpresaId(dto.getCodigoBarras(), empresaId)
                .orElseThrow(() -> new RuntimeException("Produto com código " + dto.getCodigoBarras() + " não encontrado no seu estoque."));


        if (dto.getTipo().equalsIgnoreCase("SAIDA")) {
            if (produto.getQuantidade() < dto.getQuantidade()) {
                throw new RuntimeException("Estoque insuficiente! Você tentou vender " + dto.getQuantidade() + " mas só tem " + produto.getQuantidade() + " de " + produto.getNome());
            }
            produto.setQuantidade(produto.getQuantidade() - dto.getQuantidade());
        } else if (dto.getTipo().equalsIgnoreCase("ENTRADA")) {
            produto.setQuantidade(produto.getQuantidade() + dto.getQuantidade());
        } else {
            throw new RuntimeException("Tipo de movimentação inválido.");
        }

        // Salva o novo saldo do produto
        produtoRepository.save(produto);

        //  Regista o Histórico da Movimentação
        Movimentacao mov = new Movimentacao();
        mov.setProduto(produto);
        mov.setEmpresa(produto.getEmpresa());
        mov.setTipo(TipoMovimentacao.valueOf(dto.getTipo().toUpperCase()));
        mov.setQuantidade(dto.getQuantidade());
        mov.setDataMovimentacao(java.time.LocalDateTime.now());

        return movimentacaoRepository.save(mov);
    }
}