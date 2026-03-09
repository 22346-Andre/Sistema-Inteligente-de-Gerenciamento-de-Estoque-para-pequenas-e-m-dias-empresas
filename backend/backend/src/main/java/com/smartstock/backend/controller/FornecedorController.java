package com.smartstock.backend.controller;

import com.smartstock.backend.dto.FornecedorDTO;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/fornecedores")
public class FornecedorController {

    @Autowired
    private FornecedorService service;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FornecedorRepository fornecedorRepository;

    // --- MÉTODO AUXILIAR PARA PEGAR A EMPRESA LOGADA ---
    private Usuario getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado"));
    }

    // 1. LISTAR: Só mostra os fornecedores da  empresa!
    @GetMapping
    public List<Fornecedor> listar() {
        Usuario logado = getUsuarioLogado();
        // 🚨 ATENÇÃO: Você precisará criar o método findByEmpresaId no FornecedorRepository!
        return fornecedorRepository.findByEmpresaId(logado.getEmpresa().getId());
    }

    //  2. CRIAR
    @PostMapping
    public ResponseEntity<Fornecedor> criar(@RequestBody @Valid FornecedorDTO dto) {
        // O seu FornecedorService.salvar() deve vincular o fornecedor à empresa logada!
        return ResponseEntity.ok(service.salvar(dto));
    }

    //  3. ATUALIZAR
    @PutMapping("/{id}")
    public ResponseEntity<Fornecedor> atualizar(@PathVariable Long id, @RequestBody @Valid FornecedorDTO dto) {
        Usuario logado = getUsuarioLogado();
        Fornecedor alvo = fornecedorRepository.findById(id).orElseThrow();

        if (!alvo.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Fornecedor pertence a outra empresa.");
        }
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    //  4. DELETAR
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Usuario logado = getUsuarioLogado();
        Fornecedor alvo = fornecedorRepository.findById(id).orElseThrow();

        if (!alvo.getEmpresa().getId().equals(logado.getEmpresa().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode apagar fornecedores de outra empresa.");
        }

        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}