package com.smartstock.backend.controller;

import com.smartstock.backend.dto.VendaExternaDTO;
import com.smartstock.backend.dto.VendaExternaResultadoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Rota pública (sem JWT — ver SecurityConfigurations, /api/webhooks/** é
 * permitAll) usada por canais de venda externos (Mercado Livre, Shopify,
 * etc.) pra avisar o SmartStock que um item foi vendido fora do sistema,
 * e a baixa de estoque precisa ser replicada aqui.
 *
 * Autenticação: header X-Webhook-Secret (não é JWT — é um segredo opaco
 * gerado automaticamente por empresa no cadastro, ver Empresa.webhookSecret).
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/vendas")
    public ResponseEntity<?> receberVendaExterna(
            @RequestBody VendaExternaDTO dto,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String segredoRecebido) {

       
        if (segredoRecebido == null || segredoRecebido.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Cabeçalho X-Webhook-Secret é obrigatório.");
        }

        Empresa empresa = webhookService.buscarEmpresaPorSegredo(segredoRecebido)
                .orElse(null);

        if (empresa == null) {
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Segredo de webhook inválido.");
        }


        VendaExternaResultadoDTO resultado = webhookService.processarVendaExterna(dto, empresa);
        return ResponseEntity.ok(resultado);
    }
}
