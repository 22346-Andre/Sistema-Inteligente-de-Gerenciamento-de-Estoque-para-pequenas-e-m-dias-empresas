package com.smartstock.backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;


@Service
public class PixService {

    private static final String GUI_PIX = "BR.GOV.BCB.PIX";

    public String gerarCopiaECola(String chavePix, String nomeRecebedor, String cidadeRecebedor,
                                   BigDecimal valor, String identificador) {
        if (chavePix == null || chavePix.isBlank()) {
            throw new IllegalArgumentException("Chave PIX não configurada para esta empresa.");
        }

        StringBuilder payload = new StringBuilder();

        campo(payload, "00", "01"); // Payload Format Indicator
        campo(payload, "01", "11"); // Point of Initiation Method (11 = estático, reutilizável)

        // Merchant Account Information (sub-campos aninhados: GUI + chave)
        StringBuilder merchantAccountInfo = new StringBuilder();
        campo(merchantAccountInfo, "00", GUI_PIX);
        campo(merchantAccountInfo, "01", chavePix.trim());
        campo(payload, "26", merchantAccountInfo.toString());

        campo(payload, "52", "0000"); // Merchant Category Code (genérico)
        campo(payload, "53", "986");  // Moeda: Real (ISO 4217)

        if (valor != null && valor.compareTo(BigDecimal.ZERO) > 0) {
            campo(payload, "54", valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        }

        campo(payload, "58", "BR"); // País
        campo(payload, "59", limitarESemAcento(nomeRecebedor, 25));
        campo(payload, "60", limitarESemAcento(cidadeRecebedor, 15));

        // Additional Data Field Template (txid — identifica a cobrança pro lojista)
        StringBuilder additionalData = new StringBuilder();
        String txid = (identificador == null || identificador.isBlank()) ? "***" : somenteAlfanumerico(identificador, 25);
        campo(additionalData, "05", txid);
        campo(payload, "62", additionalData.toString());

        // CRC16 é sempre o último campo, e entra no cálculo do próprio CRC
        // (incluindo o "6304" do cabeçalho do campo, sem o valor ainda).
        payload.append("6304");
        String crc = calcularCRC16(payload.toString());

        return payload.append(crc).toString();
    }

    /** Monta um campo TLV (Tag-Length-Value) no formato do BR Code. */
    private void campo(StringBuilder destino, String id, String valor) {
        String tamanho = String.format("%02d", valor.length());
        destino.append(id).append(tamanho).append(valor);
    }

    /** Remove acentos/caracteres especiais — o padrão BR Code exige ASCII puro nesses campos. */
    private String limitarESemAcento(String texto, int limite) {
        if (texto == null || texto.isBlank()) {
            texto = "NAO INFORMADO";
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]", "")
                .trim()
                .toUpperCase();
        if (normalizado.isBlank()) {
            normalizado = "NAO INFORMADO";
        }
        return normalizado.length() > limite ? normalizado.substring(0, limite) : normalizado;
    }

    private String somenteAlfanumerico(String texto, int limite) {
        String limpo = texto.replaceAll("[^A-Za-z0-9]", "");
        if (limpo.isBlank()) limpo = "***";
        return limpo.length() > limite ? limpo.substring(0, limite) : limpo;
    }

    /** CRC16-CCITT (falso, polinômio 0x1021, valor inicial 0xFFFF) — exigido pelo padrão BR Code. */
    private String calcularCRC16(String payload) {
        int polinomio = 0x1021;
        int resultado = 0xFFFF;

        byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) {
            resultado ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((resultado & 0x8000) != 0) {
                    resultado = (resultado << 1) ^ polinomio;
                } else {
                    resultado <<= 1;
                }
                resultado &= 0xFFFF;
            }
        }
        return String.format("%04X", resultado);
    }
}
