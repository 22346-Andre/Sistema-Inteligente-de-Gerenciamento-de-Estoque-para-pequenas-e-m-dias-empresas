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
        Produto produto = new Produto();
        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setQuantidade(dto.getQuantidade());
        // Se não mandar estoque mínimo, assume 5
        produto.setEstoqueMinimo(dto.getEstoqueMinimo() != null ? dto.getEstoqueMinimo() : 5);

        return repository.save(produto);
    }
}
