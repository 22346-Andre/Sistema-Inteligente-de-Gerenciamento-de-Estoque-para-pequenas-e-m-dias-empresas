package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class LoginAttemptService {

    private static final int MAX_TENTATIVAS = 5;
    private static final long JANELA_BLOQUEIO_MINUTOS = 15;

    private static class Registro {
        int tentativas;
        LocalDateTime bloqueadoAte;
    }

    private final Map<String, Registro> tentativasPorEmail = new ConcurrentHashMap<>();

    public void verificarBloqueio(String email) {
        Registro registro = tentativasPorEmail.get(normalizar(email));
        if (registro != null && registro.bloqueadoAte != null && LocalDateTime.now().isBefore(registro.bloqueadoAte)) {
            long minutosRestantes = Duration.between(LocalDateTime.now(), registro.bloqueadoAte).toMinutes() + 1;
            throw new RegraNegocioException(
                    "Muitas tentativas de login com este e-mail. Tente novamente em " + minutosRestantes + " minuto(s)."
            );
        }
    }

    public void registrarFalha(String email) {
        String chave = normalizar(email);
        Registro registro = tentativasPorEmail.computeIfAbsent(chave, k -> new Registro());
        registro.tentativas++;
        if (registro.tentativas >= MAX_TENTATIVAS) {
            registro.bloqueadoAte = LocalDateTime.now().plusMinutes(JANELA_BLOQUEIO_MINUTOS);
        }
    }

    public void registrarSucesso(String email) {
        tentativasPorEmail.remove(normalizar(email));
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
