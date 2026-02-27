package com.smartstock.backend.service;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Lote;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.LoteRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VerificadorEstoqueService {

    private final EmpresaRepository empresaRepository;
    private final ProdutoRepository produtoRepository;
    private final LoteRepository loteRepository;
    private final EmailService emailService;

    public VerificadorEstoqueService(EmpresaRepository empresaRepository, ProdutoRepository produtoRepository, LoteRepository loteRepository, EmailService emailService) {
        this.empresaRepository = empresaRepository;
        this.produtoRepository = produtoRepository;
        this.loteRepository = loteRepository;
        this.emailService = emailService;
    }

    // Roda todo dia às 08:00 da manhã para entregar o "Resumo Diário"
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void relatorioDiarioInteligente() {
        System.out.println("🤖 [IA SmartStock] Iniciando varredura de inteligência de estoque...");

        List<Empresa> empresas = empresaRepository.findAll();

        for (Empresa empresa : empresas) {
            // 1. Coleta os 3 tipos de problemas
            List<Produto> produtosCriticos = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresa.getId());

            LocalDate daqui30Dias = LocalDate.now().plusDays(30);
            List<Lote> lotesVencendo = loteRepository.findLotesPertoDoVencimento(empresa.getId(), daqui30Dias);

            LocalDateTime dezDiasAtras = LocalDateTime.now().minusDays(10);
            List<Produto> encalhados = produtoRepository.findProdutosEncalhados(empresa.getId(), dezDiasAtras);

            // 2. Se a empresa tiver QUALQUER um dos 3 problemas, monta e envia o e-mail
            if (!produtosCriticos.isEmpty() || !lotesVencendo.isEmpty() || !encalhados.isEmpty()) {
                enviarResumoDiario(empresa, produtosCriticos, lotesVencendo, encalhados);
            }
        }
        System.out.println("✅ Varredura concluída!");
    }

    private void enviarResumoDiario(Empresa empresa, List<Produto> criticos, List<Lote> vencendo, List<Produto> encalhados) {
        StringBuilder conteudo = new StringBuilder();
        conteudo.append("Olá, gestor da ").append(empresa.getNomeFantasia()).append("!\n");
        conteudo.append("Aqui é o seu assistente SmartStock. Preparei o seu resumo diário de estoque:\n\n");

        // --- BLOCO 1: COMPRAR ---
        if (!criticos.isEmpty()) {
            conteudo.append("🚨 PRECISAM DE REPOSIÇÃO (Estoque Crítico):\n");
            for (Produto p : criticos) {
                conteudo.append(" - ").append(p.getNome())
                        .append(" (Atual: ").append(p.getQuantidade())
                        .append(" | Mínimo: ").append(p.getEstoqueMinimo()).append(")\n");
            }
            conteudo.append("\n");
        }

        // --- BLOCO 2: VENDER RÁPIDO (PROMOÇÃO) ---
        if (!vencendo.isEmpty()) {
            conteudo.append("⚠️ VALIDADE PRÓXIMA (Vencem em menos de 30 dias):\n");
            for (Lote l : vencendo) {
                conteudo.append(" - ").append(l.getProduto().getNome())
                        .append(" (Lote c/ ").append(l.getQuantidade()).append(" un. | Vence em: ")
                        .append(l.getDataValidade()).append(")\n");
            }
            conteudo.append("\n");
        }

        // --- BLOCO 3: ENCALHADOS ---
        if (!encalhados.isEmpty()) {
            conteudo.append("🛑 ENCALHADOS (Sem saída há mais de 10 dias):\n");
            for (Produto p : encalhados) {
                conteudo.append(" - ").append(p.getNome()).append(" (Parados: ").append(p.getQuantidade()).append(" un.)\n");
            }
            conteudo.append("\n");
        }

        conteudo.append("----------------------------------------\n");
        conteudo.append("Acesse o sistema para tomar as ações necessárias!\n");

        String emailDestino = empresa.getEmailContato();
        if (emailDestino != null && !emailDestino.trim().isEmpty()) {
            emailService.sendEmail(
                    emailDestino,
                    "📊 Resumo Inteligente SmartStock - " + empresa.getNomeFantasia(),
                    conteudo.toString()
            );
            System.out.println("📧 E-mail de resumo disparado para: " + emailDestino);
        }
    }
}