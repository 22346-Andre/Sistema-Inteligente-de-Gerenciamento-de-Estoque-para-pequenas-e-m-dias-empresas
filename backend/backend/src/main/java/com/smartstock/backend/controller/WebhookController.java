package com.smartstock.backend.controller;

import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

  
    @PostMapping("/vendas")
    public ResponseEntity<String> receberVendaExterna(
            @RequestBody VendaExternaDTO dto,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String segredoRecebido) {

        if (segredoRecebido == null || segredoRecebido.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Cabeçalho X-Webhook-Secret é obrigatório.");
        }

        if (!webhookService.segredoValidoParaEmpresa(dto.getEmpresaId(), segredoRecebido)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Segredo de webhook inválido para essa empresa.");
        }

        String resultado = webhookService.processarVendaExterna(dto);
        return ResponseEntity.ok(resultado);
    }
}