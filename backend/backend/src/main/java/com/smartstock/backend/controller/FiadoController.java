package com.smartstock.backend.controller;

import com.smartstock.backend.dto.ContaReceberDTO;
import com.smartstock.backend.model.ContaReceber;
import com.smartstock.backend.service.FiadoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fiados")
public class FiadoController {

    private final FiadoService fiadoService;

    public FiadoController(FiadoService fiadoService) {
        this.fiadoService = fiadoService;
    }

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    @PostMapping
    public ResponseEntity<ContaReceber> registrar(@RequestBody ContaReceberDTO dto) {
        return ResponseEntity.ok(fiadoService.registrarFiado(getEmpresaIdLogada(), dto));
    }

    @GetMapping
    public ResponseEntity<List<ContaReceber>> listarCaderneta() {
        return ResponseEntity.ok(fiadoService.listarCaderneta(getEmpresaIdLogada()));
    }

    @GetMapping("/sugestoes-cobranca")
    public ResponseEntity<List<ContaReceber>> listarSugestoesCobranca() {
        return ResponseEntity.ok(fiadoService.buscarClientesParaCobrar(getEmpresaIdLogada()));
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<ContaReceber> pagar(@PathVariable Long id) {
        return ResponseEntity.ok(fiadoService.marcarComoPago(id));
    }

    @PutMapping("/{id}/adiar")
    public ResponseEntity<ContaReceber> adiar(@PathVariable Long id, @RequestParam(defaultValue = "7") int dias) {
        return ResponseEntity.ok(fiadoService.adiarCobranca(id, dias));
    }

    @GetMapping("/{id}/whatsapp")
    public ResponseEntity<Map<String, String>> obterLinkWhatsApp(@PathVariable Long id) {
        String link = fiadoService.gerarLinkCobrancaWhatsApp(id);
        return ResponseEntity.ok(Map.of("linkWhatsApp", link));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaReceber> atualizar(@PathVariable Long id, @RequestBody ContaReceberDTO dto) {

        Long empresaId = getEmpresaIdLogada();
        ContaReceber atualizado = fiadoService.atualizarFiado(id, dto, empresaId);
        return ResponseEntity.ok(atualizado);
    }
}