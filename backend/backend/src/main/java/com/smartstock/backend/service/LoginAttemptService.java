package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class LoginAttemptService {

    private static final int MAX_TENTATIVAS_EMAIL = 5;
    private static final long JANELA_BLOQUEIO_EMAIL_MINUTOS = 15;

    // Limite por IP é mais folgado que o de e-mail de propósito: um restaurante
    // ou loja pode ter várias pessoas (caixa, gerente) logando do mesmo IP/rede
    // Wi-Fi. O objetivo aqui não é travar uso legítimo, é frear alguém varrendo
    // dezenas de e-mails a partir do mesmo IP.
    private static final int MAX_TENTATIVAS_IP = 20;
    private static final long JANELA_BLOQUEIO_IP_MINUTOS = 15;

    private static class Registro {
        int tentativas;
        LocalDateTime bloqueadoAte;
    }

    private final Map<String, Registro> tentativasPorEmail = new ConcurrentHashMap<>();
    private final Map<String, Registro> tentativasPorIp = new ConcurrentHashMap<>();

    // ATENÇÃO — LIMITAÇÃO CONHECIDA: este controle vive em memória
    // (ConcurrentHashMap) da própria instância. Funciona hoje porque o backend
    // roda em UMA instância só. Se/quando for pra múltiplas instâncias atrás de
    // um load balancer (ver plano de escalonamento), cada instância vai contar
    // tentativas separadamente e o limite efetivo vira
    // "MAX_TENTATIVAS x nº de instâncias". Nesse momento isso precisa migrar
    // pra um contador compartilhado (Redis, ex. via Bucket4j) — não dá pra
    // adiar silenciosamente, tem que entrar no mesmo PR que sobe a 2ª instância.
    public void verificarBloqueio(String email, String ip) {
        verificarRegistro(tentativasPorEmail, normalizar(email),
                "Muitas tentativas de login com este e-mail. Tente novamente em %d minuto(s).");
        verificarRegistro(tentativasPorIp, normalizarIp(ip),
                "Muitas tentativas de login a partir deste endereço. Tente novamente em %d minuto(s).");
    }

    public void registrarFalha(String email, String ip) {
        registrarFalhaRegistro(tentativasPorEmail, normalizar(email), MAX_TENTATIVAS_EMAIL, JANELA_BLOQUEIO_EMAIL_MINUTOS);
        registrarFalhaRegistro(tentativasPorIp, normalizarIp(ip), MAX_TENTATIVAS_IP, JANELA_BLOQUEIO_IP_MINUTOS);
    }

    public void registrarSucesso(String email, String ip) {
        tentativasPorEmail.remove(normalizar(email));
        // Tentativa por IP é deixada decair sozinha (não zera no sucesso): um
        // IP que teve 1 login certo e 19 errados pouco depois ainda é
        // suspeito. Só o e-mail correto é "perdoado" no sucesso.
    }

    private void verificarRegistro(Map<String, Registro> mapa, String chave, String mensagem) {
        Registro registro = mapa.get(chave);
        if (registro != null && registro.bloqueadoAte != null && LocalDateTime.now().isBefore(registro.bloqueadoAte)) {
            long minutosRestantes = Duration.between(LocalDateTime.now(), registro.bloqueadoAte).toMinutes() + 1;
            throw new RegraNegocioException(String.format(mensagem, minutosRestantes));
        }
    }

    private void registrarFalhaRegistro(Map<String, Registro> mapa, String chave, int maxTentativas, long janelaMinutos) {
        if (chave.isEmpty()) return;
        Registro registro = mapa.computeIfAbsent(chave, k -> new Registro());
        registro.tentativas++;
        if (registro.tentativas >= maxTentativas) {
            registro.bloqueadoAte = LocalDateTime.now().plusMinutes(janelaMinutos);
        }
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizarIp(String ip) {
        return ip == null ? "" : ip.trim();
    }
}
