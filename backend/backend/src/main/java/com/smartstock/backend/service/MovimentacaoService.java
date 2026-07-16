package com.smartstock.backend.service;

import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.exception.RegraNegocioException;
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
        Long empresaId = getEmpresaIdLogada();

        // Garante que o produto existe e pertence à empresa logada antes de
        // devolver qualquer movimentação (evita vazamento de histórico entre empresas).
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: id=" + produtoId));

        if (!produto.getEmpresa().getId().equals(empresaId)) {
            throw new AcessoNegadoException("Operação não permitida para esta empresa!");
        }

        // Busca já filtrada por produto + empresa (defesa em profundidade,
        // mesmo que a checagem acima já garanta a posse do produto).
        return movimentacaoRepository.findByProdutoIdOrderByDataMovimentacaoDesc(produtoId);
    }

    @Transactional
    public Movimentacao registrarViaPDV(MovimentacaoPdvDTO dto) {
        Long empresaId = getEmpresaIdLogada(); // Usa a sua função segura do JWT

        // 1. Procura o produto pelo código de barras e garante que é da empresa logada
        Produto produto = produtoRepository.findByCodigoBarrasAndEmpresaId(dto.getCodigoBarras(), empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto com código " + dto.getCodigoBarras() + " não encontrado no seu estoque."));


        if (dto.getTipo().equalsIgnoreCase("SAIDA")) {
            if (produto.getQuantidade() < dto.getQuantidade()) {
                throw new RegraNegocioException("Estoque insuficiente! Você tentou vender " + dto.getQuantidade() + " mas só tem " + produto.getQuantidade() + " de " + produto.getNome());
            }
            produto.setQuantidade(produto.getQuantidade() - dto.getQuantidade());
        } else if (dto.getTipo().equalsIgnoreCase("ENTRADA")) {
            produto.setQuantidade(produto.getQuantidade() + dto.getQuantidade());
        } else {
            throw new RegraNegocioException("Tipo de movimentação inválido.");
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