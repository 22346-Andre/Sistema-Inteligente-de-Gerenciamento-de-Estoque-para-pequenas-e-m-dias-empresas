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
        // 1. Verifica se o email já existe
        if (usuarioRepository.findByEmail(dto.getEmailAdmin()).isPresent()) {
            throw new RuntimeException("Este e-mail já está em uso por outro usuário!");
        }

        // 1.5 Verifica se o CNPJ já existe no banco (Evita empresas duplicadas)
        if (empresaRepository.existsByCnpj(dto.getCnpj())) {
            throw new RuntimeException("Já existe uma empresa cadastrada com este CNPJ!");
        }

        // 2. Cria a Empresa com TODOS os dados que o banco exige
        Empresa novaEmpresa = new Empresa();
        novaEmpresa.setRazaoSocial(dto.getNomeEmpresa());
        novaEmpresa.setNomeFantasia(dto.getNomeEmpresa());
        novaEmpresa.setCnpj(dto.getCnpj());
        novaEmpresa.setEmailContato(dto.getEmailContato());

        empresaRepository.save(novaEmpresa); // Salva para gerar o ID

        // 3. Cria o Usuário ADMIN (Dono) e salva o telefone dele
        Usuario admin = new Usuario();
        admin.setNome(dto.getNomeAdmin());
        admin.setEmail(dto.getEmailAdmin());
        admin.setSenha(passwordEncoder.encode(dto.getSenhaAdmin()));
        admin.setPerfil("ADMIN");

        // --- SALVANDO O TELEFONE DO ADMIN ---
        admin.setTelefone(dto.getTelefoneAdmin());

        admin.setEmpresa(novaEmpresa);

        usuarioRepository.save(admin);

        return "Empresa e Administrador cadastrados com sucesso!";
    }
}