package com.smartstock.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente da API Pix da Delfinance (https://docs.delbank.com.br).
 *
 * Endpoint e contrato confirmados na documentação pública da Delfinance em
 * agosto/2026 — POST {baseUrl}/baas/api/v2/pix/qrcode/dynamic/, autenticado
 * via headers x-delbank-api-key + x-delfinance-account-id.
 *
 * Desligado por padrão (delfinance.enabled=false): enquanto não houver chave
 * de API configurada, isEnabled() retorna false e o FiadoService cai
 * automaticamente pro Pix "copia e cola" estático que já existia
 * (PixService), sem quebrar nada em quem ainda não contratou a Delfinance.
 */
@Service
public class DelfinanceClient {

    private static final Logger logger = LoggerFactory.getLogger(DelfinanceClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${delfinance.enabled:false}")
    private boolean enabled;

    @Value("${delfinance.base-url:https://apisandbox.delbank.com.br}")
    private String baseUrl;

    @Value("${delfinance.api-key:}")
    private String apiKey;

    @Value("${delfinance.account-id:}")
    private String accountId;

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank() && accountId != null && !accountId.isBlank();
    }

    /**
     * Resultado de uma cobrança Pix dinâmica criada na Delfinance.
     *
     * @param correlationId  o mesmo identificador enviado na criação — é ele
     *                       que volta no webhook PIX_RECEIVED pra sabermos
     *                       qual fiado foi pago.
     * @param copiaECola     o "Pix Copia e Cola" (payloadPix) — mesmo formato
     *                       que o PixCobrancaDialog do frontend já sabe
     *                       renderizar como QR Code, então não precisa mudar
     *                       nada na tela.
     */
    public record CobrancaPix(String correlationId, String copiaECola) {
    }

    /**
     * Cria uma cobrança Pix dinâmica na Delfinance pro valor informado.
     * expiresIn de 24h — tempo razoável pra um cliente de fiado pagar sem a
     * cobrança expirar no meio do dia.
     */
    public CobrancaPix criarCobrancaDinamica(BigDecimal valor, String correlationId) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "Delfinance não está configurada (delfinance.enabled=false ou faltam DELFINANCE_API_KEY / DELFINANCE_ACCOUNT_ID)."
            );
        }

        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put("correlationId", correlationId);
        corpo.put("amount", valor.doubleValue());
        corpo.put("expiresIn", 86400); // 24h
        corpo.put("formatResponse", "PAYLOAD_AND_QRCODE");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/baas/api/v2/pix/qrcode/dynamic/"))
                    .header("x-delbank-api-key", apiKey)
                    .header("x-delfinance-account-id", accountId)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                logger.warn("Delfinance retornou status {} ao criar cobrança Pix: {}", response.statusCode(), response.body());
                throw new IllegalStateException("Delfinance recusou a criação da cobrança Pix (status " + response.statusCode() + ").");
            }

            JsonNode corpoResposta = objectMapper.readTree(response.body());
            String payloadPix = corpoResposta.path("payloadPix").asText(null);

            if (payloadPix == null || payloadPix.isBlank()) {
                logger.warn("Delfinance não retornou payloadPix na resposta: {}", response.body());
                throw new IllegalStateException("Resposta inesperada da Delfinance ao criar a cobrança Pix.");
            }

            return new CobrancaPix(correlationId, payloadPix);

        } catch (java.io.IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Falha de rede ao chamar a API da Delfinance.", e);
            throw new IllegalStateException("Não foi possível conectar à Delfinance no momento.", e);
        }
    }
}
