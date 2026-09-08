package com.smartstock.backend.controller;

import com.smartstock.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * NOVO: diagnóstico de e-mail. Todo envio de e-mail do sistema é @Async +
 * try/catch silencioso (loga e some) — proposital pra não travar a resposta
 * HTTP do usuário final esperando o SMTP responder, mas isso também significa
 * que se o envio falhar (senha errada, porta SMTP bloqueada pelo provedor de
 * hospedagem, etc.), NINGUÉM vê o erro — nem o usuário, nem quem administra o
 * sistema, a não ser vasculhando log.
 *
 * Este endpoint testa o envio de forma SÍNCRONA e devolve o erro real (classe
 * da exceção + mensagem) direto na resposta HTTP, pra diagnosticar em segundos
 * em vez de vasculhar log do Render.
 */
@RestController
@RequestMapping("/admin")
public class DiagnosticoController {

    @Autowired
    private EmailService emailService;

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping("/testar-email")
    public ResponseEntity<Map<String, String>> testarEmail(@RequestParam String destino) {
        try {
            emailService.enviarEmailTeste(destino);
            return ResponseEntity.ok(Map.of(
                    "status", "sucesso",
                    "mensagem", "E-mail enviado com sucesso para " + destino + ". Verifique a caixa de entrada (e o spam)."
            ));
        } catch (Exception e) {
            String causaRaiz = e.getCause() != null ? e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage() : "";
            return ResponseEntity.status(500).body(Map.of(
                    "status", "falha",
                    "excecao", e.getClass().getName(),
                    "mensagem", e.getMessage() != null ? e.getMessage() : "(sem mensagem)",
                    "causaRaiz", causaRaiz,
                    "diagnostico", diagnosticar(e)
            ));
        }
    }

    /** Traduz a exceção técnica numa dica em português do que provavelmente está errado. */
    private String diagnosticar(Exception e) {
        String textoCompleto = (e.toString() + " " + (e.getCause() != null ? e.getCause().toString() : "")).toLowerCase();

        if (textoCompleto.contains("brevo_api_key") || textoCompleto.contains("não configurada")) {
            return "Variável de ambiente BREVO_API_KEY não está configurada no Render. Gere uma chave em "
                    + "app.brevo.com/settings/keys/api e adicione essa variável.";
        }
        if (textoCompleto.contains("401") || textoCompleto.contains("unauthorized") || textoCompleto.contains("invalid api key")) {
            return "Chave de API da Brevo inválida ou expirada (HTTP 401). Gere uma nova em app.brevo.com/settings/keys/api "
                    + "e atualize a variável BREVO_API_KEY no Render.";
        }
        if (textoCompleto.contains("400") && (textoCompleto.contains("sender") || textoCompleto.contains("not authoriz"))) {
            return "O e-mail remetente (BREVO_SENDER_EMAIL) não está verificado na conta Brevo. Vá em "
                    + "app.brevo.com > Settings > Senders & IP e verifique esse e-mail antes de conseguir enviar.";
        }
        if (textoCompleto.contains("timeout") || textoCompleto.contains("connect")) {
            return "Timeout ao tentar conectar na API da Brevo (https://api.brevo.com). Isso é incomum pra uma chamada "
                    + "HTTPS comum — verifique se o Render não está com algum bloqueio de rede geral no momento.";
        }
        return "Erro não reconhecido automaticamente — verifique a mensagem completa acima.";
    }
}