package com.smartstock.backend.service;

import com.smartstock.backend.dto.FornecedorDTO;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.repository.FornecedorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FornecedorService {

    @Autowired
    private FornecedorRepository repository;

    public List<Fornecedor> listarTodos() {
        return repository.findAll();
    }

    public Fornecedor salvar(FornecedorDTO dto) {
        // CORREÇÃO AQUI: dto.getCnpj() em vez de dto.cnpj()
        if (repository.findByCnpj(dto.getCnpj()).isPresent()) {
            throw new RuntimeException("Fornecedor com este CNPJ já existe!");
        }

        Fornecedor fornecedor = new Fornecedor();
        // CORREÇÃO AQUI: Usando os Getters do Lombok
        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());

        return repository.save(fornecedor);
    }

    public Fornecedor atualizar(Long id, FornecedorDTO dto) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        // CORREÇÃO AQUI TAMBÉM:
        fornecedor.setNome(dto.getNome());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());

        return repository.save(fornecedor);
    }

    public void deletar(Long id) {
        repository.deleteById(id);
    }
}