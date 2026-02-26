package com.smartstock.backend.controller;

import com.smartstock.backend.dto.UsuarioDTO;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService service;

    // GET - Listar tudo (Aberto para visualização corporativa geral - todos da empresa podem ver)
    @GetMapping
    public List<Usuario> listar() {
        return service.listarTodos();
    }

    // GET - Buscar por ID
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST - Criar novo (TRANCADO: Apenas gestores podem cadastrar novos funcionários)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Usuario> criar(@RequestBody @Valid UsuarioDTO dto) {
        System.out.println("🚀 SUCESSO: A requisição chegou no Controller de Criação (Autorizada)!");
        return ResponseEntity.ok(service.salvar(dto));
    }

    // PUT - Atualizar
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioDTO dto) {

        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    // DELETE - Apagar
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {

        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}