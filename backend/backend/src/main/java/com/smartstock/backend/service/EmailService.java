package com.smartstock.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartstock.backend.dto.SugestaoCompraDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;


@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final SugestaoCompraService sugestaoCompraService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:projectstock77@gmail.com}")
    private String emailRemetente;

    @Value("${brevo.sender.name:SmartStock}")
    private String nomeRemetente;

    public EmailService(SugestaoCompraService sugestaoCompraService) {
        this.sugestaoCompraService = sugestaoCompraService;
    }

    /**
     * Monta e envia a requisicao HTTP pra API da Brevo. Lanca excecao em caso
     * de falha - quem chama decide se quer engolir (metodos assincronos) ou
     * propagar (enviarEmailTeste, usado no diagnostico).
     */
    private void enviarViaBrevo(String destino, String assunto, String corpoTexto, String nomeAnexo, byte[] conteudoAnexo) throws Exception {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException(
                    "BREVO_API_KEY nao configurada. Gere uma chave em app.brevo.com/settings/keys/api e configure a variavel de ambiente BREVO_API_KEY no Render."
            );
        }

        ObjectNode corpo = objectMapper.createObjectNode();

        ObjectNode sender = corpo.putObject("sender");
        sender.put("email", emailRemetente);
        sender.put("name", nomeRemetente);

        corpo.putArray("to").addObject().put("email", destino);
        corpo.put("subject", assunto);
        corpo.put("textContent", corpoTexto);

        if (nomeAnexo != null && conteudoAnexo != null) {
            ObjectNode anexo = corpo.putArray("attachment").addObject();
            anexo.put("name", nomeAnexo);
            anexo.put("content", Base64.getEncoder().encodeToString(conteudoAnexo));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("api-key", brevoApiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Brevo devolve 201 Created em caso de sucesso. Qualquer outro status e
        // falha - propaga com o corpo da resposta (a Brevo devolve mensagens de
        // erro bem claras: chave invalida, remetente nao verificado, etc).
        if (response.statusCode() != 201) {
            throw new RuntimeException("Brevo respondeu " + response.statusCode() + ": " + response.body());
        }
    }

    // ========================================================================
    // METODO 1: E-mail Automatico (Monta o texto com o robo e estatisticas)
    // ========================================================================
    @Async("emailExecutor")
    public void enviarPlanilhaAutomatica(Long empresaId, String emailDestino) {
        try {
            List<SugestaoCompraDTO> sugestoes = sugestaoCompraService.listarSugestoesPorEmpresa(empresaId);

            long urgentes = sugestoes.stream().filter(s -> "URGENTE".equals(s.getUrgencia())).count();
            long atencao = sugestoes.stream().filter(s -> "ATENCAO".equals(s.getUrgencia())).count();
            long total = sugestoes.size();

            String assunto = "\uD83D\uDCCA Resumo de Estoque e Sugestoes de Compra - SmartStock";
            String corpoEmail = "Ola, Gestor!\n\n"
                    + "O seu relatorio diario de estoque foi gerado com sucesso.\n\n"
                    + "RESUMO DA SITUACAO:\n"
                    + urgentes + " itens com ESTOQUE ZERADO ou CRITICO (Urgente!)\n"
                    + atencao + " itens ABAIXO DO MINIMO (Atencao)\n"
                    + "Total de itens para reposicao: " + total + "\n\n"
                    + "Em anexo, voce encontrara a planilha completa com as quantidades exatas "
                    + "que precisam ser compradas (ja com a margem de seguranca e calculo da IA).\n\n"
                    + "Atenciosamente,\n"
                    + "Robo do SmartStock";

            byte[] planilhaBytes = sugestaoCompraService.gerarPlanilhaCsvPorEmpresa(empresaId);

            enviarViaBrevo(emailDestino, assunto, corpoEmail, "Planilha_Compras_SmartStock.csv", planilhaBytes);
            logger.info("E-mail automatico com RESUMO enviado com sucesso para empresaId={} destino={}", empresaId, emailDestino);

        } catch (Exception e) {
            logger.error("Erro ao enviar e-mail automatico para empresaId={} destino={}", empresaId, emailDestino, e);
        }
    }

    // ========================================================================
    // METODO 2: E-mail Generico (Recebe o texto direto do Controller)
    // ========================================================================
    @Async("emailExecutor")
    public void enviarResumoComPlanilha(String emailDestino, String assunto, String texto, Long empresaId) {
        try {
            byte[] planilhaBytes = sugestaoCompraService.gerarPlanilhaCsvPorEmpresa(empresaId);
            enviarViaBrevo(emailDestino, assunto, texto, "Planilha_Compras_SmartStock.csv", planilhaBytes);
            logger.info("E-mail inteligente enviado com sucesso para empresaId={} destino={}", empresaId, emailDestino);
        } catch (Exception e) {
            logger.error("Falha ao enviar e-mail inteligente para empresaId={} destino={}", empresaId, emailDestino, e);
        }
    }

    
    public void enviarCodigoVerificacaoCadastro(String emailDestino, String nomeDono, String codigo) throws Exception {
        String corpoEmail = "Ola" + (nomeDono != null && !nomeDono.isBlank() ? ", " + nomeDono : "") + "!\n\n"
                + "Use o codigo abaixo para confirmar seu e-mail e concluir o cadastro no SmartStock:\n\n"
                + "        " + codigo + "\n\n"
                + "Esse codigo e valido por 15 minutos. Se voce nao solicitou este cadastro, pode ignorar este e-mail.\n\n"
                + "Atenciosamente,\n"
                + "Equipe SmartStock";

        enviarViaBrevo(emailDestino, "Seu codigo de confirmacao - SmartStock", corpoEmail, null, null);
        logger.info("Codigo de verificacao de cadastro enviado para destino={}", emailDestino);
    }

    // ========================================================================
    // METODO 3: E-mail de recuperacao de senha (link com token)
    // ========================================================================
    @Async("emailExecutor")
    public void enviarEmailRecuperacaoSenha(String emailDestino, String nomeDono, String linkRecuperacao) {
        try {
            String corpoEmail = "Ola" + (nomeDono != null && !nomeDono.isBlank() ? ", " + nomeDono : "") + "!\n\n"
                    + "Recebemos um pedido para redefinir a sua senha no SmartStock.\n\n"
                    + "Clique no link abaixo para escolher uma nova senha. Esse link e valido por 1 hora:\n\n"
                    + linkRecuperacao + "\n\n"
                    + "Se voce nao pediu essa redefinicao, pode ignorar este e-mail com seguranca - "
                    + "sua senha atual continua valida e nada foi alterado.\n\n"
                    + "Atenciosamente,\n"
                    + "Equipe SmartStock";

            enviarViaBrevo(emailDestino, "Recuperacao de senha - SmartStock", corpoEmail, null, null);
            logger.info("E-mail de recuperacao de senha enviado para destino={}", emailDestino);
        } catch (Exception e) {
            logger.error("Falha ao enviar e-mail de recuperacao de senha para destino={}", emailDestino, e);
        }
    }

    // ========================================================================
    // teste de e-mail SINCRONO - usado so pelo endpoint de diagnostico
    // GET /admin/testar-email. PROPAGA a excecao (nao engole), pra o
    // controller devolver o erro real na resposta HTTP.
    // ========================================================================
    public void enviarEmailTeste(String emailDestino) throws Exception {
        enviarViaBrevo(
                emailDestino,
                "Teste de e-mail - SmartStock",
                "Se voce recebeu isto, o envio de e-mail (via Brevo) esta funcionando corretamente.",
                null, null
        );
    }
}