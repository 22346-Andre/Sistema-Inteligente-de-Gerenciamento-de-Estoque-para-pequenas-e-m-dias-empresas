package com.smartstock.backend.service;

import com.smartstock.backend.dto.UsuarioDTO;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder; // <--- IMPORTANTE
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder; // <--- A PEÇA QUE FALTAVA

    // 1. LISTAR TODOS
    public List<Usuario> listarTodos() {
        return repository.findAll();
    }

    // 2. BUSCAR POR ID
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id);
    }

    // 3. SALVAR (CREATE) - AGORA COM CRIPTOGRAFIA
    public Usuario salvar(UsuarioDTO dto) {
        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está cadastrado!");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());


        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));

        usuario.setPerfil(dto.getPerfil() != null ? dto.getPerfil() : "FUNCIONARIO");

        return repository.save(usuario);
    }

    // 4. ATUALIZAR (UPDATE)
    public Usuario atualizar(Long id, UsuarioDTO dto) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setPerfil(dto.getPerfil());



        return repository.save(usuario);
    }

    // 5. DELETAR (DELETE)
    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Usuário não encontrado!");
        }
        repository.deleteById(id);
    }
}