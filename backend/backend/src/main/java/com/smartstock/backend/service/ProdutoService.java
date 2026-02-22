package com.smartstock.backend.service;

import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository repository;

    @Autowired
    private EmpresaRepository empresaRepository;

    // --- MÉTODO AUXILIAR PARA NÃO REPETIR CÓDIGO (DRY) ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Produto> listarTodos() {
        // Retorna SÓ os produtos da empresa logada!
        return repository.findByEmpresaId(getEmpresaIdLogada());
    }

    // --- NOVO: Traz os produtos críticos SÓ da empresa logada ---
    public List<Produto> listarEstoqueCritico() {
        return repository.findProdutosComEstoqueBaixoPorEmpresa(getEmpresaIdLogada());
    }

    public Produto salvar(ProdutoDTO dto) {

        if (repository.existsByNome(dto.getNome())) {
            throw new RuntimeException("Erro: O produto '" + dto.getNome() + "' já existe no sistema! Registre uma ENTRADA nas Movimentações.");
        }

        Long empresaIdLogada = getEmpresaIdLogada();

        // Busca a Empresa real no banco. Se não achar, dá erro.
        Empresa empresa = empresaRepository.findById(empresaIdLogada)
                .orElseThrow(() -> new RuntimeException("Erro: Empresa não encontrada para o usuário logado com ID " + empresaIdLogada));

        Produto produto = new Produto();
        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setQuantidade(dto.getQuantidade());
        produto.setEstoqueMinimo(dto.getEstoqueMinimo() != null ? dto.getEstoqueMinimo() : 5);
        produto.setNcm(dto.getNcm());
        produto.setUnidade(dto.getUnidade() != null ? dto.getUnidade().toUpperCase() : "UN");

        // Marca produto com a empresa encontrada via JWT
        produto.setEmpresa(empresa);

        return repository.save(produto);
    }

    public Produto atualizar(Long id, ProdutoDTO dto) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        // TRAVA DE SEGURANÇA SAAS
        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode alterar um produto de outra empresa.");
        }

        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setEstoqueMinimo(dto.getEstoqueMinimo());
        produto.setNcm(dto.getNcm());
        produto.setUnidade(dto.getUnidade() != null ? dto.getUnidade().toUpperCase() : "UN");

        return repository.save(produto);
    }

    public void deletar(Long id) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        // TRAVA DE SEGURANÇA SAAS
        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode deletar um produto de outra empresa.");
        }

        repository.deleteById(id);
    }
}