package com.smartstock.backend.controller;

import com.smartstock.backend.dto.AbrirSessaoCaixaDTO;
import com.smartstock.backend.dto.FecharSessaoCaixaDTO;
import com.smartstock.backend.model.SessaoCaixa;
import com.smartstock.backend.service.SessaoCaixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessoes-caixa")
public class SessaoCaixaController {

    @Autowired
    private SessaoCaixaService service;

    // Qualquer usuário autenticado (CAIXA, ESTOQUISTA, ADMIN) abre/fecha o
    // PRÓPRIO caixa — isso é operação do dia a dia, não é dado sigiloso.
    @GetMapping("/atual")
    public ResponseEntity<SessaoCaixa> buscarAtual() {
        return ResponseEntity.ok(service.buscarSessaoAtual());
    }

    @PostMapping("/abrir")
    public ResponseEntity<SessaoCaixa> abrir(@RequestBody(required = false) AbrirSessaoCaixaDTO dto) {
        return ResponseEntity.ok(service.abrir(dto != null ? dto : new AbrirSessaoCaixaDTO()));
    }

    @PostMapping("/fechar")
    public ResponseEntity<SessaoCaixa> fechar(@RequestBody(required = false) FecharSessaoCaixaDTO dto) {
        return ResponseEntity.ok(service.fechar(dto != null ? dto : new FecharSessaoCaixaDTO()));
    }

    // Histórico de todos os operadores — isso sim é visão gerencial, só ADMIN.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<SessaoCaixa>> listarHistorico() {
        return ResponseEntity.ok(service.listarHistorico());
    }
}
