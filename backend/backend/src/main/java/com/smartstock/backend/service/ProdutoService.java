package com.smartstock.backend.service;

import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository repository;

    // Listar tudo
    public List<Produto> listarTodos() {
        return repository.findAll();
    }

    // Salvar (Converter DTO -> Entity)
    public Produto salvar(ProdutoDTO dto) {

        //  Verifica se já existe um produto com este nome
        if (repository.existsByNome(dto.getNome())) {
            throw new RuntimeException("Erro: O produto '" + dto.getNome() + "' já existe no sistema! Registre uma ENTRADA nas Movimentações.");
        }

        Produto produto = new Produto();
        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setQuantidade(dto.getQuantidade());

        // Se não mandar estoque mínimo, assume 5
        produto.setEstoqueMinimo(dto.getEstoqueMinimo() != null ? dto.getEstoqueMinimo() : 5);

        return repository.save(produto);
    }

    // Método para ATUALIZAR apenas dados cadastrais
    public Produto atualizar(Long id, ProdutoDTO dto) {
        // 1. Busca o produto pelo ID
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        // 2. Atualiza apenas os dados permitidos
        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setEstoqueMinimo(dto.getEstoqueMinimo());



        return repository.save(produto);
    }

    // Método para APAGAR
    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Produto não encontrado com o ID: " + id);
        }
        repository.deleteById(id);
    }
}