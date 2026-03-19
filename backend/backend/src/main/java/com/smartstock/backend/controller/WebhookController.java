package com.smartstock.backend.controller;

import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.service.WebhookService;
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
    public ResponseEntity<String> receberVendaExterna(@RequestBody VendaExternaDTO dto) {
        String resultado = webhookService.processarVendaExterna(dto);
        return ResponseEntity.ok(resultado);
    }
}