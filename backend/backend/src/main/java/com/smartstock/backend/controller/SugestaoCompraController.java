package com.smartstock.backend.controller;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import com.smartstock.backend.service.SugestaoCompraService;
import com.smartstock.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sugestoes-compra")
public class SugestaoCompraController {

    @Autowired
    private SugestaoCompraService service;

    @Autowired
    private EmailService emailService;

    // Descobre qual é a empresa do usuário logado
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    // Rota que alimenta a TELA
    @GetMapping
    public ResponseEntity<List<SugestaoCompraDTO>> listarSugestoes() {
        return ResponseEntity.ok(service.listarSugestoes());
    }

    // Rota que BAIXA A PLANILHA EXCEL
    @GetMapping("/planilha")
    public ResponseEntity<byte[]> baixarPlanilha() {
        byte[] csvBytes = service.gerarPlanilhaCsv();

        HttpHeaders headers = new HttpHeaders();

        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Planilha_Compras_SmartStock.csv");
        headers.add(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    //
    @PostMapping("/enviar-email")
    public ResponseEntity<String> enviarPlanilhaPorEmail(@RequestParam String emailDestino) {
        try {
            Long empresaId = getEmpresaIdLogada();
            // Chama o serviço de e-mail passando a empresa e o destino digitado na tela
            emailService.enviarPlanilhaAutomatica(empresaId, emailDestino);
            return ResponseEntity.ok("Planilha enviada com sucesso para " + emailDestino);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao enviar e-mail: " + e.getMessage());
        }
    }
}