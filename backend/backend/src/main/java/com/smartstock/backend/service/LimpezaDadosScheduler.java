package com.smartstock.backend.service;

import com.smartstock.backend.repository.MovimentacaoRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LimpezaDadosScheduler {

    private final MovimentacaoRepository movimentacaoRepository;

    // ⚠️ DESLIGADO POR PADRÃO. Esse job rodava todo dia às 3h apagando
    // PERMANENTEMENTE qualquer Movimentacao com mais de 7 dias, de TODAS as
    // empresas — sem distinção. Isso destruía o histórico que Curva ABC,
    // Giro de Estoque, Estoque Morto e os relatórios em PDF precisam (todos
    // olham janelas de 30/90 dias); com retenção de 7 dias, essas análises
    // nunca tinham chance de funcionar de verdade. Além do impacto no
    // sistema, é literalmente o histórico de vendas do negócio — para fins
    // fiscais/contábeis no Brasil, esse tipo de registro normalmente precisa
    // ser mantido por anos, não dias.
    // Se um dia precisar reativar (ex.: banco de homologação/teste enchendo
    // demais), configure LIMPEZA_MOVIMENTACOES_ENABLED=true e
    // LIMPEZA_MOVIMENTACOES_DIAS_RETENCAO no ambiente (nunca em produção com
    // dado real sem ter certeza absoluta do período de retenção exigido).
    @Value("${limpeza.movimentacoes.enabled:false}")
    private boolean limpezaHabilitada;

    @Value("${limpeza.movimentacoes.dias-retencao:730}")
    private int diasRetencao;

    public LimpezaDadosScheduler(MovimentacaoRepository movimentacaoRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
    }

    /**
     * 🟢 Executa todos os dias às 03:00 da manhã — mas só apaga algo se
     * limpeza.movimentacoes.enabled=true estiver explicitamente configurado
     * (padrão é false, ver comentário acima).
     * Cron: "Segundos Minutos Horas Dia Mes DiaDaSemana"
     * "0 0 3 * * ?" = Às 03:00:00 de todos os dias.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "limparHistoricoAntigo", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void limparHistoricoAntigo() {
        if (!limpezaHabilitada) {
            return;
        }

        System.out.println("[CRON] A iniciar a limpeza do histórico de movimentações antigas (retenção: " + diasRetencao + " dias)...");

        LocalDateTime dataLimite = LocalDateTime.now().minusDays(diasRetencao);

        try {
            movimentacaoRepository.deleteByDataMovimentacaoBefore(dataLimite);
            System.out.println("[CRON] Limpeza concluída com sucesso! Registos com mais de " + diasRetencao + " dias foram apagados.");
        } catch (Exception e) {
            System.err.println("[CRON] Erro ao tentar limpar o histórico: " + e.getMessage());
        }
    }
}