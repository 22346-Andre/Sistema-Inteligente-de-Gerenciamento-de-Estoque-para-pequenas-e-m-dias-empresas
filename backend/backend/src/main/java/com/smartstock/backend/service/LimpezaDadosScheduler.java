package com.smartstock.backend.service;

import com.smartstock.backend.repository.MovimentacaoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LimpezaDadosScheduler {

    private final MovimentacaoRepository movimentacaoRepository;

    public LimpezaDadosScheduler(MovimentacaoRepository movimentacaoRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
    }

    /**
     * 🟢 Executa todos os dias às 03:00 da manhã.
     * Cron: "Segundos Minutos Horas Dia Mes DiaDaSemana"
     * "0 0 3 * * ?" = Às 03:00:00 de todos os dias.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void limparHistoricoAntigo() {
        System.out.println("[CRON] A iniciar a limpeza do histórico de movimentações antigas...");

        // Calcula a data de 7 dias atrás (exatamente 1 semana)
        LocalDateTime umaSemanaAtras = LocalDateTime.now().minusDays(7);

        // Chama o método do repositório para apagar tudo o que for mais antigo que essa data
        try {
            movimentacaoRepository.deleteByDataMovimentacaoBefore(umaSemanaAtras);
            System.out.println("[CRON] Limpeza concluída com sucesso! Registos com mais de 7 dias foram apagados.");
        } catch (Exception e) {
            System.err.println("[CRON] Erro ao tentar limpar o histórico: " + e.getMessage());
        }
    }
}