package com.smartstock.backend.service;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.repository.EmpresaRepository;
import jakarta.transaction.Transactional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Antes, esse service apagava a empresa inteira direto ao completar 5 meses
 * sem login — sem aviso nenhum. Agora tem carência: 30 dias antes do prazo
 * final, dispara um e-mail avisando; só se ninguém logar durante esses 30
 * dias é que a exclusão acontece de verdade. Fazer login a qualquer momento
 * (antes ou depois do aviso) cancela a exclusão automaticamente, porque
 * ultimoAcesso é atualizado no login (AuthController) e o próximo ciclo do
 * job já não encontra mais essa empresa como candidata.
 */
@Service
public class CleanService {

    // Prazo total de inatividade até a exclusão (igual ao valor original).
    private static final int MESES_ATE_EXCLUSAO = 5;
    // Carência entre o aviso por e-mail e a exclusão de fato.
    private static final int DIAS_DE_CARENCIA_APOS_AVISO = 30;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private EmailService emailService;

    // Roda todo dia às 03:00 da manhã (Expressão Cron)
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "apagarEmpresasInativas", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void executarFaxinaDiaria() {
        avisarEmpresasQuaseInativas();
        apagarEmpresasAvisadasHaMaisDe30Dias();
    }

    /**
     * Passo 1: dispara o aviso por e-mail pra quem está a 30 dias do prazo
     * final de inatividade e ainda não recebeu nenhum aviso. Marca
     * avisoInatividadeEnviadoEm pra não avisar de novo todo dia.
     */
    private void avisarEmpresasQuaseInativas() {
        LocalDateTime dataCorteAviso = LocalDateTime.now()
                .minusMonths(MESES_ATE_EXCLUSAO)
                .plusDays(DIAS_DE_CARENCIA_APOS_AVISO);

        List<Empresa> quaseInativas = empresaRepository
                .findByUltimoAcessoBeforeAndAvisoInatividadeEnviadoEmIsNull(dataCorteAviso);

        for (Empresa empresa : quaseInativas) {
            if (empresa.getEmailContato() == null || empresa.getEmailContato().isBlank()) {
                // Sem e-mail de contato cadastrado não tem como avisar — loga
                // pra alguém do time olhar manualmente em vez de apagar às
                // cegas ou travar o job inteiro.
                System.err.println("[FAXINA] Empresa id=" + empresa.getId() + " está quase inativa mas não tem email_contato cadastrado — pulei o aviso.");
                continue;
            }
            emailService.enviarAvisoInatividade(empresa.getEmailContato(), empresa.getNomeFantasia());
            empresa.setAvisoInatividadeEnviadoEm(LocalDateTime.now());
            empresaRepository.save(empresa);
        }

        if (!quaseInativas.isEmpty()) {
            System.out.println("[FAXINA] Aviso de inatividade enviado para " + quaseInativas.size() + " empresa(s).");
        }
    }

    /**
     * Passo 2: apaga de verdade só quem foi avisado há 30+ dias E continua
     * sem logar desde então (ultimoAcesso não avançou).
     */
    private void apagarEmpresasAvisadasHaMaisDe30Dias() {
        LocalDateTime dataCorteAviso = LocalDateTime.now().minusDays(DIAS_DE_CARENCIA_APOS_AVISO);
        LocalDateTime dataCorteAcesso = LocalDateTime.now().minusMonths(MESES_ATE_EXCLUSAO);

        List<Empresa> paraExcluir = empresaRepository
                .findByAvisoInatividadeEnviadoEmBeforeAndUltimoAcessoBefore(dataCorteAviso, dataCorteAcesso);

        if (!paraExcluir.isEmpty()) {
            empresaRepository.deleteAll(paraExcluir);
            System.out.println("🚨 [FAXINA] " + paraExcluir.size() + " empresa(s) apagada(s) — avisadas há mais de "
                    + DIAS_DE_CARENCIA_APOS_AVISO + " dias e sem login desde então.");
        }
    }
}
