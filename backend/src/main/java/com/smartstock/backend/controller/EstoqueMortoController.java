package com.smartstock.backend.controller;

import com.smartstock.backend.dto.EstoqueMortoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.service.EstoqueMortoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/estoque-morto")
public class EstoqueMortoController {

    @Autowired
    private EstoqueMortoService service;

    @Autowired
    private EmpresaRepository empresaRepository;

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    // Dado financeiro (dinheiro parado em R$) — mesma regra de acesso das outras
    // rotas financeiras (/estatisticas): só gestores.
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<Map<String, Object>> listar() {
        List<EstoqueMortoDTO> itens = service.listarEstoqueMorto();
        BigDecimal total = service.calcularTotalCongelado(itens);
        int diasConsiderados = service.getDiasParaEstoqueMortoDaEmpresaLogada();
        return ResponseEntity.ok(Map.of(
                "itens", itens,
                "totalCongelado", total,
                "diasConsiderados", diasConsiderados
        ));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/planilha")
    public ResponseEntity<byte[]> baixarPlanilhaQueima() {
        byte[] csvBytes = service.gerarPlanilhaQueimaCsv();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Queima_Estoque_SmartStock.csv");
        headers.add(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    // Deixa o gestor ajustar o "quantos dias sem venda é considerado morto" pro
    // ritmo do próprio negócio (uma loja de roupa de inverno não é um mercadinho).
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/configuracao")
    public ResponseEntity<?> atualizarConfiguracao(@RequestBody Map<String, Integer> body) {
        Integer dias = body.get("dias");
        if (dias == null || dias <= 0) {
            return ResponseEntity.badRequest().body("Informe um número de dias maior que zero.");
        }
        if (dias > 3650) {
            return ResponseEntity.badRequest().body("Esse número de dias parece grande demais (máximo 3650, ~10 anos).");
        }

        Empresa empresa = empresaRepository.findById(getEmpresaIdLogada())
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada."));
        empresa.setDiasParaEstoqueMorto(dias);
        empresaRepository.save(empresa);

        return ResponseEntity.ok(Map.of("diasConsiderados", dias));
    }
}