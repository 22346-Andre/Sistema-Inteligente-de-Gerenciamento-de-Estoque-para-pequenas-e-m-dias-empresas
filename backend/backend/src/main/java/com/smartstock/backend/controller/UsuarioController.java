package com.smartstock.backend.controller;

import com.smartstock.backend.dto.UsuarioDTO;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService service;

    @Autowired
    private UsuarioRepository repository;

    //  Só mostra os funcionários da PRÓPRIA empresa (usando o método findByEmpresaId!)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public List<Usuario> listar() {
        Usuario logado = getUsuarioLogado();
        return repository.findByEmpresaId(logado.getEmpresa().getId());
    }

    //  BUSCAR POR ID: Só permite se o funcionário for da mesma empresa
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable Long id) {
        Usuario logado = getUsuarioLogado();
        Usuario alvo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        if (!alvo.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado. Funcionário pertence a outra empresa.");
        }

        return ResponseEntity.ok(alvo);
    }

    // POST - Criar novo
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Usuario> criar(@RequestBody @Valid UsuarioDTO dto) {
        System.out.println("🚀 SUCESSO: A requisição chegou no Controller de Criação (Autorizada)!");

        return ResponseEntity.ok(service.salvar(dto));
    }

    //  ATUALIZAR: Impede que atualizem dados de um funcionário vizinho
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioDTO dto) {
        Usuario logado = getUsuarioLogado();
        Usuario alvo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        if (!alvo.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode alterar funcionários de outra empresa.");
        }

        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    // DELETAR: Anti-Suicídio e Anti-Espionagem
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Usuario logado = getUsuarioLogado();

        // 1. Regra Anti-Suicídio
        if (logado.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode excluir sua própria conta de Administrador!");
        }

        Usuario alvo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado"));

        // 2. Isolamento de Tenant (Vizinhos)
        if (!alvo.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode apagar funcionários de outra empresa!");
        }

        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

    // --- MÉTODO AUXILIAR ---
    // Captura quem é a pessoa que fez a requisição olhando pro Token JWT
    private Usuario getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return repository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado"));
    }

    //  Devolve quem é o usuário dono do Token atual
    @GetMapping("/me")
    public ResponseEntity<Usuario> buscarUsuarioLogado() {
        Usuario logado = getUsuarioLogado();
        return ResponseEntity.ok(logado);
    }
}