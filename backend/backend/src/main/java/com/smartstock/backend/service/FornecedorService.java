package com.smartstock.backend.service;

import com.smartstock.backend.dto.FornecedorDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FornecedorService {

    @Autowired
    private FornecedorRepository repository;

    @Autowired
    private EmpresaRepository empresaRepository;

    // --- MÉTODO AUXILIAR DA CONFIANÇA ZERO (JWT) ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Fornecedor> listarTodos() {

        return repository.findByEmpresaId(getEmpresaIdLogada());
    }

    public Fornecedor salvar(FornecedorDTO dto) {
        Long empresaId = getEmpresaIdLogada();

        // Verifica duplicidade apenas dentro da própria empresa
        if (repository.findByCnpjAndEmpresaId(dto.getCnpj(), empresaId).isPresent()) {
            throw new RuntimeException("Você já possui um fornecedor cadastrado com este CNPJ!");
        }

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada"));

        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());
        fornecedor.setEmpresa(empresa);

        return repository.save(fornecedor);
    }

    public Fornecedor atualizar(Long id, FornecedorDTO dto) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        // TRAVA DE SEGURANÇA SAAS
        if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Este fornecedor pertence a outra empresa.");
        }

        // Verifica se ele não está tentando mudar para um CNPJ que já existe (excluindo ele mesmo)
        repository.findByCnpjAndEmpresaId(dto.getCnpj(), getEmpresaIdLogada())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new RuntimeException("Já existe outro fornecedor com este CNPJ.");
                    }
                });

        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());

        return repository.save(fornecedor);
    }

    public void deletar(Long id) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        // TRAVA DE SEGURANÇA SAAS
        if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode deletar um fornecedor de outra empresa.");
        }

        repository.deleteById(id);
    }
}