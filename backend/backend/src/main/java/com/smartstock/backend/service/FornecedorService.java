package com.smartstock.backend.service;

import com.smartstock.backend.dto.FornecedorDTO;
import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FornecedorService {

    private final FornecedorRepository repository;
    private final EmpresaRepository empresaRepository;
    private final ProdutoRepository produtoRepository; 

    // Injeção de dependência via construtor (Clean Code: dependências explícitas e imutáveis)
    public FornecedorService(FornecedorRepository repository,
                             EmpresaRepository empresaRepository,
                             ProdutoRepository produtoRepository) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.produtoRepository = produtoRepository;
    }

    // --- MÉTODO AUXILIAR DA CONFIANÇA ZERO (JWT) ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Fornecedor> listarTodos() {
        return repository.findByEmpresaId(getEmpresaIdLogada());
    }

    public Fornecedor salvar(FornecedorDTO dto) {
        Long empresaId = getEmpresaIdLogada();

        if (repository.findByCnpjAndEmpresaId(dto.getCnpj(), empresaId).isPresent()) {
            throw new RegraNegocioException("Você já possui um fornecedor cadastrado com este CNPJ!");
        }

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());
        fornecedor.setPrazoEntregaDias(dto.getPrazoEntregaDias());
        fornecedor.setCategoriasFornecidas(dto.getCategoriasFornecidas());
        fornecedor.setEmpresa(empresa);

        return repository.save(fornecedor);
    }

    public Fornecedor atualizar(Long id, FornecedorDTO dto) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado"));

        if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Este fornecedor pertence a outra empresa.");
        }

        repository.findByCnpjAndEmpresaId(dto.getCnpj(), getEmpresaIdLogada())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new RegraNegocioException("Já existe outro fornecedor com este CNPJ.");
                    }
                });

        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());
        fornecedor.setPrazoEntregaDias(dto.getPrazoEntregaDias());
        fornecedor.setCategoriasFornecidas(dto.getCategoriasFornecidas());

        return repository.save(fornecedor);
    }

    public void deletar(Long id) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado"));

        // TRAVA DE SEGURANÇA SAAS
        if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Você não pode deletar um fornecedor de outra empresa.");
        }

        // Validação de negócio ANTES de tocar no banco para evitar erro de Foreign Key
        if (produtoRepository.existsByFornecedorId(id)) {
            throw new RegraNegocioException(
                "Não é possível excluir este fornecedor porque ele está vinculado a produtos do seu estoque. " +
                "Remova o vínculo desses produtos ou cadastre outro fornecedor para eles antes de excluir."
            );
        }

        repository.deleteById(id);
    }
}