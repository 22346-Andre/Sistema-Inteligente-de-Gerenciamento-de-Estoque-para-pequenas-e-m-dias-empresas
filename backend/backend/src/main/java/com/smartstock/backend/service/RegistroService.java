package com.smartstock.backend.service;

import com.smartstock.backend.dto.RegistroEmpresaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public String registrarNovaEmpresa(RegistroEmpresaDTO dto) {
        // Usa o nome atualizado: getEmail()
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está em uso por outro usuário!");
        }

        if (empresaRepository.existsByCnpj(dto.getCnpj())) {
            throw new RuntimeException("Já existe uma empresa cadastrada com este CNPJ!");
        }

        Empresa novaEmpresa = new Empresa();
        // Usa o nome atualizado: getRazaoSocial()
        novaEmpresa.setRazaoSocial(dto.getRazaoSocial());
        novaEmpresa.setCnpj(dto.getCnpj());
        novaEmpresa.setEmailContato(dto.getEmailContato());
        novaEmpresa.setNomeFantasia(dto.getNomeFantasia());
        novaEmpresa.setTelefone(dto.getTelefoneEmpresa());

        empresaRepository.save(novaEmpresa);

        Usuario admin = new Usuario();
        // Usa o nome atualizado: getNomeDono()
        admin.setNome(dto.getNomeDono());
        // Usa o nome atualizado: getEmail()
        admin.setEmail(dto.getEmail());
        // Usa o nome atualizado: getSenha()
        admin.setSenha(passwordEncoder.encode(dto.getSenha()));
        admin.setPerfil("ADMIN");
        admin.setTelefone(dto.getTelefoneAdmin());
        admin.setEmpresa(novaEmpresa);

        usuarioRepository.save(admin);

        return "Empresa e Administrador cadastrados com sucesso!";
    }
}