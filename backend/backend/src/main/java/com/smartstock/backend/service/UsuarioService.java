package com.smartstock.backend.service;

import com.smartstock.backend.dto.UsuarioDTO;
import com.smartstock.backend.model.Empresa;
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

    // --- NOVO MÉTODO AUXILIAR: BUSCAR O USUÁRIO LOGADO INTEIRO ---
    private Usuario getUsuarioLogado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof Jwt jwt) {
            // Assume-se que o email foi colocado no "Subject" ou "sub" ao gerar o JWT
            String email = jwt.getSubject();
            return repository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Erro: Usuário logado não encontrado na base de dados."));
        }
        throw new RuntimeException("Acesso negado: Usuário não autenticado ou token inválido.");
    }

    // 1. LISTAR TODOS
    public List<Usuario> listarTodos() {
        Usuario logado = getUsuarioLogado();

        // O SUPER_ADMIN vê todos os usuários da plataforma. O ADMIN vê só da sua empresa.
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

            // Regra do Cercadinho no GET
            if (!"SUPER_ADMIN".equals(logado.getPerfil()) && !usuario.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Este usuário pertence a outra empresa.");
            }
        }
        return usuarioOpt;
    }

    // 3. SALVAR ( Apenas ADMIN cria funcionários)
    public Usuario salvar(UsuarioDTO dto) {
        Usuario adminLogado = getUsuarioLogado(); // Descobre quem é o chefe logado

        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está cadastrado!");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));

        // Ninguém cria SUPER_ADMIN
        if ("SUPER_ADMIN".equals(dto.getPerfil())) {
            throw new RuntimeException("Acesso negado: Você não tem permissão para criar um SUPER_ADMIN.");
        }

        // Define como USER (ou ADMIN, se for o caso), mas o padrão é USER.
        usuario.setPerfil(dto.getPerfil() != null ? dto.getPerfil() : "USER");

        //  O novo usuário é OBRIGATORIAMENTE da mesma empresa do ADMIN que está cadastrando
        usuario.setEmpresa(adminLogado.getEmpresa());

        return repository.save(usuario);
    }

    // 4. ATUALIZAR )
    public Usuario atualizar(Long id, UsuarioDTO dto) {
        Usuario adminLogado = getUsuarioLogado();
        Usuario usuarioAlvo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        // Regra da Imunidade Superior
        if ("SUPER_ADMIN".equals(usuarioAlvo.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Acesso negado: Um ADMIN não pode alterar um SUPER_ADMIN.");
        }

        // Regra do Cercadinho
        if (!"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            if (!usuarioAlvo.getEmpresa().getId().equals(adminLogado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Você não pode alterar um funcionário de outra empresa.");
            }
        }

        //  Impedir que um ADMIN transforme alguém em SUPER_ADMIN
        if ("SUPER_ADMIN".equals(dto.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Acesso negado: Apenas o dono do sistema pode promover um usuário a SUPER_ADMIN.");
        }

        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada com o ID: " + dto.getEmpresaId()));

        usuarioAlvo.setNome(dto.getNome());
        usuarioAlvo.setEmail(dto.getEmail());
        usuarioAlvo.setPerfil(dto.getPerfil());
        usuarioAlvo.setEmpresa(empresa);

        return repository.save(usuarioAlvo);
    }

    // 5. DELETAR
    public void deletar(Long id) {
        Usuario adminLogado = getUsuarioLogado();
        Usuario usuarioAlvo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        // 1. Regra do Não Suicídio
        if (adminLogado.getId().equals(usuarioAlvo.getId())) {
            throw new RuntimeException("Operação inválida: Você não pode deletar a sua própria conta.");
        }

        // 2. Regra da Imunidade Superior
        if ("SUPER_ADMIN".equals(usuarioAlvo.getPerfil()) && !"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            throw new RuntimeException("Acesso negado: Um ADMIN não tem autoridade para apagar um SUPER_ADMIN.");
        }

        // 3. Regra do Cercadinho
        if (!"SUPER_ADMIN".equals(adminLogado.getPerfil())) {
            if (!usuarioAlvo.getEmpresa().getId().equals(adminLogado.getEmpresa().getId())) {
                throw new RuntimeException("Acesso negado: Você não pode apagar um funcionário de outra empresa.");
            }
        }

        repository.deleteById(id);
    }
}