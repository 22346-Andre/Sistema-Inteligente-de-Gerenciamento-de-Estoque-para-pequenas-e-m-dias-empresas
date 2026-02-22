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

    // --- MÉTODO AUXILIAR  (JWT) ---
    private Long getEmpresaIdLogada() {
        // Verifica se a requisição tem um token válido antes de tentar extrair (útil pois o /usuarios é aberto no cadastro)
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof Jwt) {
            Jwt jwt = (Jwt) principal;
            Long empresaId = jwt.getClaim("empresaId");
            if (empresaId == null) {
                throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
            }
            return empresaId;
        }
        throw new RuntimeException("Acesso negado: Usuário não autenticado ou token inválido.");
    }

    // 1. LISTAR TODOS (BLINDADO)
    public List<Usuario> listarTodos() {
        // A MÁGICA: Retorna apenas os funcionários da empresa do gestor logado!
        return repository.findByEmpresaId(getEmpresaIdLogada());
    }

    // 2. BUSCAR POR ID (BLINDADO)
    public Optional<Usuario> buscarPorId(Long id) {
        Optional<Usuario> usuarioOpt = repository.findById(id);

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            // Trava: O usuário que eu estou tentando ver é da minha empresa?
            if (!usuario.getEmpresa().getId().equals(getEmpresaIdLogada())) {
                throw new RuntimeException("Acesso negado: Este usuário pertence a outra empresa.");
            }
        }
        return usuarioOpt;
    }

    // 3. SALVAR (Aberto: pois o dono da empresa pode estar a se cadastrar pela 1ª vez)
    public Usuario salvar(UsuarioDTO dto) {
        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Este e-mail já está cadastrado!");
        }

        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada com o ID: " + dto.getEmpresaId()));

        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        usuario.setPerfil(dto.getPerfil() != null ? dto.getPerfil() : "FUNCIONARIO");
        usuario.setEmpresa(empresa);

        return repository.save(usuario);
    }

    // 4. ATUALIZAR (BLINDADO)
    public Usuario atualizar(Long id, UsuarioDTO dto) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        // Trava de segurança
        if (!usuario.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode alterar um funcionário de outra empresa.");
        }

        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada com o ID: " + dto.getEmpresaId()));

        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setPerfil(dto.getPerfil());
        usuario.setEmpresa(empresa);

        return repository.save(usuario);
    }

    // 5. DELETAR (BLINDADO)
    public void deletar(Long id) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        // Trava de segurança
        if (!usuario.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode deletar um funcionário de outra empresa.");
        }

        repository.deleteById(id);
    }
}