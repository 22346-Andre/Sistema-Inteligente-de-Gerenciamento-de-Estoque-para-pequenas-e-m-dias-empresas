package com.smartstock.backend.service;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SugestaoCompraService sugestaoCompraService;

    public void enviarPlanilhaAutomatica(Long empresaId, String emailDestino) {
        try {
            // 1. O Robô analisa os dados antes de escrever o e-mail
            List<SugestaoCompraDTO> sugestoes = sugestaoCompraService.listarSugestoesPorEmpresa(empresaId);

            long urgentes = sugestoes.stream().filter(s -> s.getUrgencia().equals("URGENTE")).count();
            long atencao = sugestoes.stream().filter(s -> s.getUrgencia().equals("ATENCAO")).count();
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
                    + "🔴 " + urgentes + " itens com ESTOQUE ZERADO (Urgente!)\n"
                    + "🟡 " + atencao + " itens ABAIXO DO MÍNIMO (Atenção)\n"
                    + "📦 Total de itens para reposição: " + total + "\n\n"
                    + "Em anexo, você encontrará a planilha completa com as quantidades exatas "
                    + "que precisam ser compradas (já com a margem de segurança).\n\n"
                    + "Atenciosamente,\n"
                    + "Robô do SmartStock 🤖";

            helper.setText(corpoEmail);

            // 3. Anexa a Planilha
            byte[] planilhaBytes = sugestaoCompraService.gerarPlanilhaCsvPorEmpresa(empresaId);
            ByteArrayResource anexo = new ByteArrayResource(planilhaBytes);
            helper.addAttachment("Planilha_Compras_SmartStock.csv", anexo);

            mailSender.send(mensagem);
            System.out.println("✅ E-mail com RESUMO enviado para a empresa ID: " + empresaId);

        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar e-mail automático: " + e.getMessage());
        }
    }
    public void enviarResumoComPlanilha(String emailDestino, String assunto, String texto, Long empresaId) {
        try {
            jakarta.mail.internet.MimeMessage mensagem = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(mensagem, true, "UTF-8");

            helper.setFrom("projectstock77@gmail.com");
            helper.setTo(emailDestino);
            helper.setSubject(assunto);
            helper.setText(texto);

            // Pega a planilha da empresa logada e anexa!
            byte[] planilhaBytes = sugestaoCompraService.gerarPlanilhaCsvPorEmpresa(empresaId);
            org.springframework.core.io.ByteArrayResource anexo = new org.springframework.core.io.ByteArrayResource(planilhaBytes);
            helper.addAttachment("Planilha_Compras_SmartStock.csv", anexo);

            mailSender.send(mensagem);
            System.out.println("✅ E-mail Inteligente + Planilha enviado para: " + emailDestino);

        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar e-mail inteligente: " + e.getMessage());
        }
    }
}