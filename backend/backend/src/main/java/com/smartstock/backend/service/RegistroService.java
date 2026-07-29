package com.smartstock.backend.service;

import com.smartstock.backend.dto.RegistroEmpresaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Perfis;
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

    // extraído pra ser reaproveitado ANTES de mandar o código de
    // verificação (VerificacaoCadastroService) — não faz sentido gastar um
    // envio de e-mail/código pra um cadastro que já ia falhar de qualquer jeito
    // por e-mail ou CNPJ duplicado.
    public void validarDisponibilidade(RegistroEmpresaDTO dto) {
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está em uso por outro usuário!");
        }

        if (empresaRepository.existsByCnpj(dto.getCnpj())) {
            throw new RuntimeException("Já existe uma empresa cadastrada com este CNPJ!");
        }
    }

    @Transactional
    public String registrarNovaEmpresa(RegistroEmpresaDTO dto) {
        validarDisponibilidade(dto);

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
        admin.setPerfil(Perfis.ADMIN);
        admin.setTelefone(dto.getTelefoneAdmin());
        admin.setEmpresa(novaEmpresa);
        admin.setDono(true); // Este é o único momento em que "dono" é marcado como true.

        usuarioRepository.save(admin);

        return "Empresa e Administrador cadastrados com sucesso!";
    }
}
