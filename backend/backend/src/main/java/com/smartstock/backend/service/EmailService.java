package com.smartstock.backend.service;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final SugestaoCompraService sugestaoCompraService;

    // Injeção via construtor (Clean Code: dependências explícitas e imutáveis)
    public EmailService(JavaMailSender mailSender, SugestaoCompraService sugestaoCompraService) {
        this.mailSender = mailSender;
        this.sugestaoCompraService = sugestaoCompraService;
    }

    // ========================================================================
    // MÉTODO 1: E-mail Automático (Monta o texto com o robô e estatísticas)
    // ========================================================================
    @Async("emailExecutor")
    public void enviarPlanilhaAutomatica(Long empresaId, String emailDestino) {
        try {
            // 1. O Robô analisa os dados antes de escrever o e-mail
            List<SugestaoCompraDTO> sugestoes = sugestaoCompraService.listarSugestoesPorEmpresa(empresaId);

            long urgentes = sugestoes.stream().filter(s -> "URGENTE".equals(s.getUrgencia())).count();
            long atencao = sugestoes.stream().filter(s -> "ATENCAO".equals(s.getUrgencia())).count();
            long total = sugestoes.size();

            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");

            helper.setFrom("projectstock77@gmail.com");
            helper.setTo(emailDestino);
            helper.setSubject("📊 Resumo de Estoque e Sugestões de Compra - SmartStock");

            // 2. O Texto do E-mail (O Resumo Rápido para o Dono)
            String corpoEmail = "Olá, Gestor!\n\n"
                    + "O seu relatório diário de estoque foi gerado com sucesso.\n\n"
                    + "📋 **RESUMO DA SITUAÇÃO:**\n"
                    + "🔴 " + urgentes + " itens com ESTOQUE ZERADO ou CRÍTICO (Urgente!)\n"
                    + "🟡 " + atencao + " itens ABAIXO DO MÍNIMO (Atenção)\n"
                    + "📦 Total de itens para reposição: " + total + "\n\n"
                    + "Em anexo, você encontrará a planilha completa com as quantidades exatas "
                    + "que precisam ser compradas (já com a margem de segurança e cálculo da IA).\n\n"
                    + "Atenciosamente,\n"
                    + "Robô do SmartStock 🤖";

            helper.setText(corpoEmail);

            // 3. Anexa a Planilha
            byte[] planilhaBytes = sugestaoCompraService.gerarPlanilhaCsvPorEmpresa(empresaId);
            helper.addAttachment("Planilha_Compras_SmartStock.csv", new ByteArrayResource(planilhaBytes));

            mailSender.send(mensagem);
            logger.info("✅ E-mail automático com RESUMO enviado com sucesso para empresaId={} destino={}", empresaId, emailDestino);

        } catch (Exception e) {
            logger.error("❌ Erro ao enviar e-mail automático para empresaId={} destino={}", empresaId, emailDestino, e);
        }
    }

    // ========================================================================
    // MÉTODO 2: E-mail Genérico (Recebe o texto direto do Controller)
    // ========================================================================
    @Async("emailExecutor")
    public void enviarResumoComPlanilha(String emailDestino, String assunto, String texto, Long empresaId) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");

            helper.setFrom("projectstock77@gmail.com");
            helper.setTo(emailDestino);
            helper.setSubject(assunto);
            helper.setText(texto);

            // Pega a planilha da empresa logada e anexa!
            byte[] planilhaBytes = sugestaoCompraService.gerarPlanilhaCsvPorEmpresa(empresaId);
            helper.addAttachment("Planilha_Compras_SmartStock.csv", new ByteArrayResource(planilhaBytes));

            mailSender.send(mensagem);
            logger.info("✅ E-mail inteligente enviado com sucesso para empresaId={} destino={}", empresaId, emailDestino);

        } catch (Exception e) {
            // Log completo em vez de engolir o erro.
            logger.error("❌ Falha ao enviar e-mail inteligente para empresaId={} destino={}", empresaId, emailDestino, e);
        }
    }

    // ========================================================================
    // MÉTODO 3: 🟢 NOVO — E-mail de recuperação de senha (link com token)
    // ========================================================================
    @Async("emailExecutor")
    public void enviarEmailRecuperacaoSenha(String emailDestino, String nomeDono, String linkRecuperacao) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");

            helper.setFrom("projectstock77@gmail.com");
            helper.setTo(emailDestino);
            helper.setSubject("🔑 Recuperação de senha - SmartStock");

            String corpoEmail = "Olá" + (nomeDono != null && !nomeDono.isBlank() ? ", " + nomeDono : "") + "!\n\n"
                    + "Recebemos um pedido para redefinir a sua senha no SmartStock.\n\n"
                    + "Clique no link abaixo para escolher uma nova senha. Esse link é válido por 1 hora:\n\n"
                    + linkRecuperacao + "\n\n"
                    + "Se você não pediu essa redefinição, pode ignorar este e-mail com segurança — "
                    + "sua senha atual continua válida e nada foi alterado.\n\n"
                    + "Atenciosamente,\n"
                    + "Equipe SmartStock 🔒";

            helper.setText(corpoEmail);
            mailSender.send(mensagem);
            logger.info("✅ E-mail de recuperação de senha enviado para destino={}", emailDestino);
        } catch (Exception e) {
            logger.error("❌ Falha ao enviar e-mail de recuperação de senha para destino={}", emailDestino, e);
        }
    }
}