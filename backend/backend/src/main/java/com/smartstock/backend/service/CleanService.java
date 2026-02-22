package com.smartstock.backend.service;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.repository.EmpresaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CleanService {

    @Autowired
    private EmpresaRepository empresaRepository;

    // Roda todo dia às 03:00 da manhã (Expressão Cron)
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void apagarEmpresasInativas() {
        System.out.println("🧹 [FAXINA] Iniciando verificação de empresas inativas...");

        // Calcula a data de 5 meses atrás
        LocalDateTime dataCorte = LocalDateTime.now().minusMonths(5);

        // Busca todas as empresas que não logam desde antes da data de corte
        List<Empresa> empresasInativas = empresaRepository.findByUltimoAcessoBefore(dataCorte);

        if (!empresasInativas.isEmpty()) {
            // Apaga todas elas do banco de dados!
            empresaRepository.deleteAll(empresasInativas);
            System.out.println("🚨 [FAXINA] " + empresasInativas.size() + " empresas foram APAGADAS por inatividade (mais de 5 meses).");
        } else {
            System.out.println("✅ [FAXINA] Nenhuma empresa inativa encontrada hoje.");
        }
    }
}