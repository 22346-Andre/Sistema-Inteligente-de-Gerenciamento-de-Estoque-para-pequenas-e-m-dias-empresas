package com.smartstock.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartstock.backend.service.FiadoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Rota pública (sem JWT — POST /api/webhooks/** já é permitAll em
 * SecurityConfigurations, ver WebhookController) que recebe os eventos Pix
 * da Delfinance (https://docs.delbank.com.br/pt/Webhooks/).
 *
 * Configure essa URL no painel da Delfinance (ou via POST /baas/api/v1/webhooks
 * com eventType "PIX_RECEIVED") apontando pra:
 *   https://<seu-backend>/api/webhooks/delfinance/pix
 *
 */
@RestController
@RequestMapping("/api/webhooks/delfinance")
public class DelfinanceWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(DelfinanceWebhookController.class);

    private final FiadoService fiadoService;

    @Value("${delfinance.webhook-secret:}")
    private String webhookSecretConfigurado;

    public DelfinanceWebhookController(FiadoService fiadoService) {
        this.fiadoService = fiadoService;
    }

    @PostMapping("/pix")
    public ResponseEntity<?> receberEventoPix(
            @RequestBody JsonNode corpo,
            @RequestHeader(value = "Authorization", required = false) String authorizationRecebido) {

        if (webhookSecretConfigurado == null || webhookSecretConfigurado.isBlank()) {
            logger.warn("Recebido webhook da Delfinance, mas delfinance.webhook-secret não está configurado no backend. Ignorando por segurança.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        // Aceita tanto "Authorization: <segredo>" (esquema HEADER) quanto
        // "Authorization: Bearer <segredo>" (esquema BEARER) — tira o
        // prefixo "Bearer " se ele vier, e compara o resto.
        String valorRecebido = authorizationRecebido == null ? null : authorizationRecebido.trim();
        if (valorRecebido != null && valorRecebido.regionMatches(true, 0, "Bearer ", 0, 7)) {
            valorRecebido = valorRecebido.substring(7).trim();
        }

        if (valorRecebido == null || !valorRecebido.equals(webhookSecretConfigurado)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credencial de webhook inválida.");
        }

        String eventType = corpo.path("eventType").asText("");
        String correlationId = corpo.path("correlationId").asText(null);

        // Só nos importa a confirmação de recebimento (cobranças de fiado).
        // Outros eventTypes (ex.: PIX_REFUNDED) são apenas confirmados com
        // 200 OK e ignorados — a Delfinance reenvia em retry se não receber 2xx.
        if ("PIX_RECEIVED".equals(eventType) && correlationId != null && correlationId.startsWith("FIADO-")) {
            fiadoService.marcarComoPagoPorCorrelationId(correlationId);
        } else {
            logger.info("Webhook Delfinance recebido e ignorado (eventType={}, correlationId={}).", eventType, correlationId);
        }

        return ResponseEntity.ok().build();
    }
}
