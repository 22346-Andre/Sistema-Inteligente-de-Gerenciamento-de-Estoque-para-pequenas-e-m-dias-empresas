package com.smartstock.backend.service;

import com.smartstock.backend.dto.EmpresaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.repository.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmpresaService {

    @Autowired
    private EmpresaRepository repository;

    public List<Empresa> listarTodas() {
        return repository.findAll();
    }

    public Empresa salvar(EmpresaDTO dto) {
        if (repository.existsByCnpj(dto.getCnpj())) {
            throw new RuntimeException("Erro: Já existe uma empresa com este CNPJ (" + dto.getCnpj() + ") no sistema!");
        }

        Empresa empresa = new Empresa();
        empresa.setCnpj(dto.getCnpj());
        empresa.setRazaoSocial(dto.getRazaoSocial());
        empresa.setNomeFantasia(dto.getNomeFantasia());

        return repository.save(empresa);
    }
}
