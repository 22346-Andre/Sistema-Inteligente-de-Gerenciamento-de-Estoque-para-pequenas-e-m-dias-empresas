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

    public List<Usuario> listarTodos() {
        Usuario logado = getUsuarioLogado();
        if ("SUPER_ADMIN".equals(logado.getPerfil())) {
            return repository.findAll();
        }
        return repository.findByEmpresaId(logado.getEmpresa().getId());
    }

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

    public Usuario salvar(UsuarioDTO dto) {
        Usuario adminLogado = getUsuarioLogado();

        if (dto.getNome() == null || dto.getNome().trim().isEmpty()) {
            throw new RuntimeException("O nome do funcionário é obrigatório.");
        }
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new RuntimeException("O e-mail é obrigatório.");
        }
        if (!dto.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new RuntimeException("Formato de e-mail inválido.");
        }
        if (dto.getSenha() == null || dto.getSenha().trim().isEmpty()) {
            throw new RuntimeException("A senha é obrigatória.");
        }
        if (dto.getSenha().length() < 6) {
            throw new RuntimeException("A senha deve ter no mínimo 6 caracteres.");
        }
        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está cadastrado!");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        usuario.setTelefone(dto.getTelefone());

        if ("SUPER_ADMIN".equals(dto.getPerfil())) {
            throw new RuntimeException("Acesso negado: Você não tem permissão para criar um SUPER_ADMIN.");
        }

        usuario.setPerfil(dto.getPerfil() != null ? dto.getPerfil() : "USER");
        usuario.setEmpresa(adminLogado.getEmpresa());

        return repository.save(usuario);
    }

    public Usuario atualizar(Long id, UsuarioDTO dto) {
        Usuario adminLogado = getUsuarioLogado();
        Usuario usuarioAlvo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        //  Busca quem é o dono (O primeiro usuário criado para aquela empresa)
        Usuario donoDaEmpresa = repository.findFirstByEmpresaIdOrderByIdAsc(usuarioAlvo.getEmpresa().getId()).orElse(null);

        //  O Dono da empresa não pode ser editado por aqui
        if (donoDaEmpresa != null && donoDaEmpresa.getId().equals(usuarioAlvo.getId())) {
            throw new RuntimeException("Ação Proibida: A conta do Dono da empresa é protegida e não pode ser editada.");
        }

        //  Um ADMIN comum não pode editar um SUPER_ADMIN do sistema
        if ("SUPER_ADMIN".equals(usuarioAlvo.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Ação Proibida: Você não tem permissão para editar um Super Administrador.");
        }

        if (!"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            if (!usuarioAlvo.getEmpresa().getId().equals(adminLogado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Empresa divergente.");
            }
        }

        usuarioAlvo.setNome(dto.getNome());
        usuarioAlvo.setEmail(dto.getEmail());
        usuarioAlvo.setPerfil(dto.getPerfil());
        usuarioAlvo.setTelefone(dto.getTelefone());

        return repository.save(usuarioAlvo);
    }

    public void deletar(Long id) {
        Usuario adminLogado = getUsuarioLogado();
        Usuario usuarioAlvo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        // Busca quem é o dono da empresa
        Usuario donoDaEmpresa = repository.findFirstByEmpresaIdOrderByIdAsc(usuarioAlvo.getEmpresa().getId()).orElse(null);

        // Impedir exclusão APENAS do dono
        if (donoDaEmpresa != null && donoDaEmpresa.getId().equals(usuarioAlvo.getId())) {
            throw new RuntimeException("Ação Bloqueada: O Dono (criador) da empresa não pode ser removido do sistema.");
        }

        //  Impedir exclusão de um SUPER_ADMIN por um usuário normal
        if ("SUPER_ADMIN".equals(usuarioAlvo.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Ação Bloqueada: Você não tem autoridade para excluir um Super Administrador.");
        }

        if (adminLogado.getId().equals(usuarioAlvo.getId())) {
            throw new RuntimeException("Você não pode deletar sua própria conta.");
        }

        if (!"SUPER_ADMIN".equals(adminLogado.getPerfil()) && !usuarioAlvo.getEmpresa().getId().equals(adminLogado.getEmpresa().getId())) {
            throw new RuntimeException("Acesso negado.");
        }

        repository.deleteById(id);
    }
}