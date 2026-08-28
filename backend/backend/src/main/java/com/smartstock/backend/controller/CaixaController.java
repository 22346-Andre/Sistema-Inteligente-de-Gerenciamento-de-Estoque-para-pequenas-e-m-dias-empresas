package com.smartstock.backend.controller;

import com.smartstock.backend.dto.LancamentoCaixaDTO;
import com.smartstock.backend.model.MovimentoCaixa;
import com.smartstock.backend.service.CaixaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/caixa")
public class CaixaController {

    private final CaixaService caixaService;

    public CaixaController(CaixaService caixaService) {
        this.caixaService = caixaService;
    }

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/extrato")
    public ResponseEntity<List<MovimentoCaixa>> listarExtrato() {
        return ResponseEntity.ok(caixaService.listarExtrato(getEmpresaIdLogada()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/saldo")
    public ResponseEntity<Map<String, BigDecimal>> obterSaldo() {
        return ResponseEntity.ok(Map.of("saldo", caixaService.obterSaldoAtual(getEmpresaIdLogada())));
    }

    // Lançamento manual — só Aporte de Sócio / Retirada de Sócio / Outro
    // (o service valida e rejeita qualquer outra origem, ver CaixaService).
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/lancamento")
    public ResponseEntity<MovimentoCaixa> registrarLancamentoManual(@RequestBody LancamentoCaixaDTO dto) {
        return ResponseEntity.ok(caixaService.registrarLancamentoManual(getEmpresaIdLogada(), dto));
    }
}
