package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitação de tentativas de login e de envio de e-mails públicos.
 *
 * Três contadores de falha de login, todos com janela de 15 minutos que
 * EXPIRA sozinha (depois da janela ou do bloqueio o contador recomeça do zero):
 *  - par e-mail + IP (5 falhas): quem erra a senha várias vezes é freado, mas
 *    um atacante em outro IP NÃO consegue travar o login do dono da conta;
 *  - e-mail, de qualquer IP (30 falhas): segura força bruta distribuída;
 *  - IP (20 falhas): segura quem varre muitos e-mails do mesmo endereço. É mais
 *    folgado de propósito, porque uma loja pode ter vários funcionários na mesma rede.
 *
 * LIMITAÇÃO CONHECIDA: o estado vive em memória da instância. Com mais de uma
 * instância atrás de um load balancer, cada uma conta separado e o limite efetivo
 * vira "limite x nº de instâncias". Nesse momento é preciso migrar para um
 * contador compartilhado (Redis, ex. via Bucket4j), no mesmo PR que subir a 2ª instância.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FALHAS_EMAIL_E_IP = 5;
    private static final int MAX_FALHAS_EMAIL = 30;
    private static final int MAX_FALHAS_IP = 20;
    private static final Duration JANELA = Duration.ofMinutes(15);

    // Teto de entradas por mapa, para o mapa não crescer sem limite sob varredura.
    private static final int MAX_ENTRADAS = 10_000;

    private static final class Registro {
        final Instant inicioJanela;
        int tentativas;
        Instant bloqueadoAte;

        Registro(Instant inicioJanela) {
            this.inicioJanela = inicioJanela;
        }
    }

    private final Map<String, Registro> falhasPorEmailEIp = new ConcurrentHashMap<>();
    private final Map<String, Registro> falhasPorEmail = new ConcurrentHashMap<>();
    private final Map<String, Registro> falhasPorIp = new ConcurrentHashMap<>();
    private final Map<String, Registro> usosPorChave = new ConcurrentHashMap<>();

    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // Visível ao pacote para os testes controlarem o relógio.
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------- login por e-mail e senha

    public void verificarBloqueio(String email, String ip) {
        String e = normalizar(email);
        String i = normalizarIp(ip);
        verificar(falhasPorEmailEIp, e + "|" + i,
                "Muitas tentativas de login com este e-mail. Tente novamente em %d minuto(s).");
        verificar(falhasPorEmail, e,
                "Muitas tentativas de login com este e-mail. Tente novamente em %d minuto(s).");
        verificar(falhasPorIp, i,
                "Muitas tentativas de login a partir deste endereço. Tente novamente em %d minuto(s).");
    }

    public void registrarFalha(String email, String ip) {
        String e = normalizar(email);
        String i = normalizarIp(ip);
        registrar(falhasPorEmailEIp, e + "|" + i, MAX_FALHAS_EMAIL_E_IP);
        registrar(falhasPorEmail, e, MAX_FALHAS_EMAIL);
        registrar(falhasPorIp, i, MAX_FALHAS_IP);
    }

    public void registrarSucesso(String email, String ip) {
        // Só o par e-mail+IP é perdoado. O contador do IP segue decaindo pela janela
        // (um IP com 1 acerto e 19 erros seguidos ainda é suspeito), e o do e-mail
        // também, para o acerto de alguém não zerar a contagem contra aquela conta.
        falhasPorEmailEIp.remove(normalizar(email) + "|" + normalizarIp(ip));
    }

    // ---------------------------------------------------------------- login social (só há o IP)

    public void verificarBloqueioIp(String ip) {
        verificar(falhasPorIp, normalizarIp(ip),
                "Muitas tentativas de login a partir deste endereço. Tente novamente em %d minuto(s).");
    }

    public void registrarFalhaIp(String ip) {
        registrar(falhasPorIp, normalizarIp(ip), MAX_FALHAS_IP);
    }

    // ---------------------------------------------------------------- endpoints públicos que enviam e-mail

    /**
     * Conta cada chamada (não só falhas) na janela de 15 minutos e barra quando passa do máximo.
     * Usado em cadastro, reenvio de código e "esqueci a senha", para não virar máquina de spam.
     * A chave deve incluir a finalidade, por exemplo "esqueci-senha|" + ip.
     */
    public void consumirLimite(String chave, int maximo) {
        String k = chave == null ? "" : chave.trim().toLowerCase();
        if (k.isEmpty()) return;

        Instant agora = clock.instant();
        Registro r = usosPorChave.compute(k, (chaveMapa, atual) -> {
            Registro reg = (atual == null || expirou(atual, agora)) ? new Registro(agora) : atual;
            reg.tentativas++;
            if (reg.tentativas > maximo && reg.bloqueadoAte == null) {
                reg.bloqueadoAte = agora.plus(JANELA);
            }
            return reg;
        });
        limparSeNecessario(usosPorChave, agora);

        if (r.tentativas > maximo) {
            long minutos = minutosRestantes(r, agora);
            throw new RegraNegocioException(
                    String.format("Muitas solicitações em pouco tempo. Tente novamente em %d minuto(s).", minutos));
        }
    }

    // ---------------------------------------------------------------- internos

    private void verificar(Map<String, Registro> mapa, String chave, String mensagem) {
        Registro registro = mapa.get(chave);
        if (registro == null || registro.bloqueadoAte == null) return;

        Instant agora = clock.instant();
        if (agora.isBefore(registro.bloqueadoAte)) {
            throw new RegraNegocioException(String.format(mensagem, minutosRestantes(registro, agora)));
        }
    }

    private void registrar(Map<String, Registro> mapa, String chave, int maximo) {
        if (chave.isEmpty() || chave.equals("|")) return;

        Instant agora = clock.instant();
        mapa.compute(chave, (k, atual) -> {
            // Janela vencida (ou bloqueio já cumprido): recomeça do zero.
            Registro reg = (atual == null || expirou(atual, agora)) ? new Registro(agora) : atual;
            reg.tentativas++;
            if (reg.tentativas >= maximo) {
                reg.bloqueadoAte = agora.plus(JANELA);
            }
            return reg;
        });
        limparSeNecessario(mapa, agora);
    }

    private boolean expirou(Registro r, Instant agora) {
        if (r.bloqueadoAte != null) {
            return !agora.isBefore(r.bloqueadoAte);
        }
        return !agora.isBefore(r.inicioJanela.plus(JANELA));
    }

    private long minutosRestantes(Registro r, Instant agora) {
        if (r.bloqueadoAte == null) return 1;
        return Duration.between(agora, r.bloqueadoAte).toMinutes() + 1;
    }

    private void limparSeNecessario(Map<String, Registro> mapa, Instant agora) {
        if (mapa.size() <= MAX_ENTRADAS) return;
        mapa.entrySet().removeIf(e -> expirou(e.getValue(), agora));
        if (mapa.size() > MAX_ENTRADAS) {
            // Ainda cheio: descarta contadores parciais, mas mantém quem já está bloqueado.
            mapa.entrySet().removeIf(e -> e.getValue().bloqueadoAte == null);
        }
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizarIp(String ip) {
        return ip == null ? "" : ip.trim();
    }
}
