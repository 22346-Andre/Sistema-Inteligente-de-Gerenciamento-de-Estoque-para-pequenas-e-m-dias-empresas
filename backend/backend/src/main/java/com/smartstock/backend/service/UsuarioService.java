package com.smartstock.backend.service;

import com.smartstock.backend.dto.UsuarioDTO;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario getUsuarioLogado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof Jwt jwt) {
            String email = jwt.getSubject();
            return repository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Erro: Usuário logado não encontrado na base de dados."));
        }
        throw new RuntimeException("Acesso negado: Usuário não autenticado ou token inválido.");
    }

    // 1. LISTAR TODOS
    public List<Usuario> listarTodos() {
        Usuario logado = getUsuarioLogado();

        if ("SUPER_ADMIN".equals(logado.getPerfil())) {
            return repository.findAll();
        }
        return repository.findByEmpresaId(logado.getEmpresa().getId());
    }

    // 2. BUSCAR POR ID
    public Optional<Usuario> buscarPorId(Long id) {
        Usuario logado = getUsuarioLogado();
        Optional<Usuario> usuarioOpt = repository.findById(id);

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (!"SUPER_ADMIN".equals(logado.getPerfil()) && !usuario.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Este usuário pertence a outra empresa.");
            }
        }
        return usuarioOpt;
    }

    // 3. SALVAR
    public Usuario salvar(UsuarioDTO dto) {
        Usuario adminLogado = getUsuarioLogado();

        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está cadastrado!");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));

        // CORREÇÃO: Salvando o Telefone!
        usuario.setTelefone(dto.getTelefone());

        if ("SUPER_ADMIN".equals(dto.getPerfil())) {
            throw new RuntimeException("Acesso negado: Você não tem permissão para criar um SUPER_ADMIN.");
        }

        usuario.setPerfil(dto.getPerfil() != null ? dto.getPerfil() : "USER");
        usuario.setEmpresa(adminLogado.getEmpresa());

        return repository.save(usuario);
    }

    // 4. ATUALIZAR
    public Usuario atualizar(Long id, UsuarioDTO dto) {
        Usuario adminLogado = getUsuarioLogado();
        Usuario usuarioAlvo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        if ("SUPER_ADMIN".equals(usuarioAlvo.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Acesso negado: Um ADMIN não pode alterar um SUPER_ADMIN.");
        }

        if (!"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            if (!usuarioAlvo.getEmpresa().getId().equals(adminLogado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Você não pode alterar um funcionário de outra empresa.");
            }
        }

        if ("SUPER_ADMIN".equals(dto.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Acesso negado: Apenas o dono do sistema pode promover um usuário a SUPER_ADMIN.");
        }

        usuarioAlvo.setNome(dto.getNome());
        usuarioAlvo.setEmail(dto.getEmail());
        usuarioAlvo.setPerfil(dto.getPerfil());
        usuarioAlvo.setTelefone(dto.getTelefone());

        return repository.save(usuarioAlvo);
    }

    // 5. DELETAR
    public void deletar(Long id) {
        Usuario adminLogado = getUsuarioLogado();
        Usuario usuarioAlvo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        if (adminLogado.getId().equals(usuarioAlvo.getId())) {
            throw new RuntimeException("Operação inválida: Você não pode deletar a sua própria conta.");
        }

        if ("SUPER_ADMIN".equals(usuarioAlvo.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Acesso negado: Um ADMIN não tem autoridade para apagar um SUPER_ADMIN.");
        }

        if (!"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            if (!usuarioAlvo.getEmpresa().getId().equals(adminLogado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Você não pode apagar um funcionário de outra empresa.");
            }
        }

        repository.deleteById(id);
    }
}