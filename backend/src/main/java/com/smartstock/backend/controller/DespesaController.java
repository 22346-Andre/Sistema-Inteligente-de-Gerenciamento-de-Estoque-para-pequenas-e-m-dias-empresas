package com.smartstock.backend.controller;

import com.smartstock.backend.dto.DespesaDTO;
import com.smartstock.backend.model.Despesa;
import com.smartstock.backend.service.DespesaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/despesas")
public class DespesaController {

    private final DespesaService despesaService;

    public DespesaController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    // Registro de despesa é operação financeira — só ADMIN/SUPER_ADMIN,
    // mesmo padrão dos outros endpoints financeiros do sistema (Curva ABC,
    // Estoque Morto etc.).
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Despesa> registrar(@RequestBody DespesaDTO dto) {
        return ResponseEntity.ok(despesaService.registrar(getEmpresaIdLogada(), dto));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<Despesa>> listar() {
        return ResponseEntity.ok(despesaService.listar(getEmpresaIdLogada()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/em-aberto")
    public ResponseEntity<List<Despesa>> listarEmAberto() {
        return ResponseEntity.ok(despesaService.listarEmAberto(getEmpresaIdLogada()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/pagar")
    public ResponseEntity<Despesa> pagar(@PathVariable Long id) {
        return ResponseEntity.ok(despesaService.marcarComoPaga(id, getEmpresaIdLogada()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Despesa> atualizar(@PathVariable Long id, @RequestBody DespesaDTO dto) {
        return ResponseEntity.ok(despesaService.atualizar(id, dto, getEmpresaIdLogada()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        despesaService.excluir(id, getEmpresaIdLogada());
        return ResponseEntity.noContent().build();
    }
}
