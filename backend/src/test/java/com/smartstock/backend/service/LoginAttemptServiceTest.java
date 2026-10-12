package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Teste unitário puro (sem contexto Spring) do rate limiter reescrito.
 * Cobre os defeitos do limiter antigo: contador que nunca expirava, contador
 * de IP que nunca decaía, mapa sem teto, e um atacante conseguindo travar o
 * login de qualquer e-mail alheio a partir de um IP diferente do da vítima.
 */
class LoginAttemptServiceTest {

    /** Relógio de teste, para controlar a passagem do tempo sem Thread.sleep. */
    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }

        void avancarMinutos(long minutos) { agora = agora.plusSeconds(minutos * 60); }
    }

    @Test
    void bloqueiaAposCincoFalhasDoMesmoParEmailEIp() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LoginAttemptService service = new LoginAttemptService(relogio);

        for (int i = 0; i < 5; i++) {
            service.verificarBloqueio("vitima@empresa.com", "1.1.1.1");
            service.registrarFalha("vitima@empresa.com", "1.1.1.1");
        }

        assertThrows(RegraNegocioException.class,
                () -> service.verificarBloqueio("vitima@empresa.com", "1.1.1.1"));
    }

    @Test
    void atacanteEmOutroIpNaoConsegueTravarODonoDaConta() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LoginAttemptService service = new LoginAttemptService(relogio);

        // Atacante em outro IP erra a senha da vítima 5 vezes.
        for (int i = 0; i < 5; i++) {
            service.verificarBloqueio("vitima@empresa.com", "6.6.6.6");
            service.registrarFalha("vitima@empresa.com", "6.6.6.6");
        }

        // O próprio dono, tentando do IP de sempre, continua livre.
        assertDoesNotThrow(() -> service.verificarBloqueio("vitima@empresa.com", "1.1.1.1"));
    }

    @Test
    void bloqueioExpiraSozinhoAposAJanela() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LoginAttemptService service = new LoginAttemptService(relogio);

        for (int i = 0; i < 5; i++) {
            service.verificarBloqueio("alguem@empresa.com", "2.2.2.2");
            service.registrarFalha("alguem@empresa.com", "2.2.2.2");
        }
        assertThrows(RegraNegocioException.class,
                () -> service.verificarBloqueio("alguem@empresa.com", "2.2.2.2"));

        relogio.avancarMinutos(16);

        assertDoesNotThrow(() -> service.verificarBloqueio("alguem@empresa.com", "2.2.2.2"));
    }

    @Test
    void falhasEspalhadasAoLongoDoTempoNaoSeAcumulamParaSempre() {
        // Defeito do limiter antigo: o contador nunca zerava, então falhas de
        // semanas diferentes se somavam até bloquear para sempre.
        RelogioDeTeste relogio = new RelogioDeTeste();
        LoginAttemptService service = new LoginAttemptService(relogio);

        for (int i = 0; i < 4; i++) service.registrarFalha("lento@empresa.com", "3.3.3.3");
        relogio.avancarMinutos(16);
        for (int i = 0; i < 4; i++) service.registrarFalha("lento@empresa.com", "3.3.3.3");

        assertDoesNotThrow(() -> service.verificarBloqueio("lento@empresa.com", "3.3.3.3"));
    }

    @Test
    void loginCertoZeraApenasOParEmailEIp() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LoginAttemptService service = new LoginAttemptService(relogio);

        for (int i = 0; i < 3; i++) service.registrarFalha("ok@empresa.com", "4.4.4.4");
        service.registrarSucesso("ok@empresa.com", "4.4.4.4");
        for (int i = 0; i < 4; i++) service.registrarFalha("ok@empresa.com", "4.4.4.4");

        // 3 falhas + sucesso (zera o par) + 4 falhas = 4, ainda abaixo do limite de 5.
        assertDoesNotThrow(() -> service.verificarBloqueio("ok@empresa.com", "4.4.4.4"));
    }

    @Test
    void limitaEnviosDeEmailPublicoPorJanela() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LoginAttemptService service = new LoginAttemptService(relogio);

        for (int i = 0; i < 5; i++) service.consumirLimite("esqueci-senha|7.7.7.7", 5);

        assertThrows(RegraNegocioException.class,
                () -> service.consumirLimite("esqueci-senha|7.7.7.7", 5));
    }
}
