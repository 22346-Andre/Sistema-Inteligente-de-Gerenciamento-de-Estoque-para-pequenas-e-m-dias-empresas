package com.smartstock.backend.controller;

import com.smartstock.backend.dto.EmpresaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/empresas")
public class EmpresaController {

    @Autowired
    private EmpresaService service;

    // Apenas o suporte (SUPER_ADMIN) pode listar todas as empresas do sistema
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping
    public List<Empresa> listar() {
        return service.listarTodas();
    }

    // Apenas o suporte (SUPER_ADMIN) pode criar uma empresa de forma manual/isolada
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Empresa> cadastrar(@RequestBody @Valid EmpresaDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }
}