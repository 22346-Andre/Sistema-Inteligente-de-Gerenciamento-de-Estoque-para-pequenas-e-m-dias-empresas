package com.smartstock.backend.service;

import com.smartstock.backend.dto.ContaReceberDTO;
import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.ContaReceber;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.StatusConta;
import com.smartstock.backend.repository.ContaReceberRepository;
import com.smartstock.backend.repository.EmpresaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
public class FiadoService {

    private static final Logger logger = LoggerFactory.getLogger(FiadoService.class);

    private final ContaReceberRepository contaRepository;
    private final EmpresaRepository empresaRepository;
    private final PixService pixService;
    private final DelfinanceClient delfinanceClient;

    public FiadoService(ContaReceberRepository contaRepository, EmpresaRepository empresaRepository,
                         PixService pixService, DelfinanceClient delfinanceClient) {
        this.contaRepository = contaRepository;
        this.empresaRepository = empresaRepository;
        this.pixService = pixService;
        this.delfinanceClient = delfinanceClient;
    }

    public ContaReceber registrarFiado(Long empresaId, ContaReceberDTO dto) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: id=" + empresaId));

        ContaReceber conta = new ContaReceber();
        conta.setEmpresa(empresa);
        conta.setNomeCliente(dto.getNomeCliente());
        conta.setTelefoneCliente(dto.getTelefoneCliente());
        conta.setValor(dto.getValor());
        conta.setDescricao(dto.getDescricao());
        conta.setDataVencimento(dto.getDataVencimento());
        conta.setStatus(StatusConta.PENDENTE);

        if (dto.getDataProximaCobranca() != null) {
            conta.setDataProximaCobranca(dto.getDataProximaCobranca()); // O lojista escolheu a data
        } else {
            conta.setDataProximaCobranca(dto.getDataVencimento());
        }

        return contaRepository.save(conta);
    }

    public List<ContaReceber> listarCaderneta(Long empresaId) {
        return contaRepository.findByEmpresaIdOrderByDataVencimentoAsc(empresaId);
    }

    public ContaReceber marcarComoPago(Long id, Long empresaId) {
        ContaReceber conta = buscarContaDaEmpresa(id, empresaId);
        conta.setStatus(StatusConta.PAGO);
        return contaRepository.save(conta);
    }

    public ContaReceber adiarCobranca(Long id, int diasParaAdiar, Long empresaId) {
        ContaReceber conta = buscarContaDaEmpresa(id, empresaId);
        conta.setDataProximaCobranca(LocalDate.now().plusDays(diasParaAdiar));
        return contaRepository.save(conta);
    }

    public String gerarLinkCobrancaWhatsApp(Long id, Long empresaId) {
        ContaReceber conta = buscarContaDaEmpresa(id, empresaId);

        String telefoneLimpo = conta.getTelefoneCliente().replaceAll("[^0-9]", "");
        if (!telefoneLimpo.startsWith("55")) telefoneLimpo = "55" + telefoneLimpo;

        String mensagem = String.format(
                "Olá %s! Tudo bem? Passando para lembrar da sua notinha na nossa loja no valor de R$ %.2f. Podemos atualizar essa pendência hoje?",
                conta.getNomeCliente(), conta.getValor()
        );

        String textoCodificado = URLEncoder.encode(mensagem, StandardCharsets.UTF_8);
        return "https://wa.me/" + telefoneLimpo + "?text=" + textoCodificado;
    }

    
    
    public String gerarCobrancaPix(Long id, Long empresaId) {
        ContaReceber conta = buscarContaDaEmpresa(id, empresaId);
        Empresa empresa = conta.getEmpresa();

        if (delfinanceClient.isEnabled()) {
            try {
                String correlationId = "FIADO-" + conta.getId();
                DelfinanceClient.CobrancaPix cobranca = delfinanceClient.criarCobrancaDinamica(conta.getValor(), correlationId);

                conta.setPixCorrelationId(cobranca.correlationId());
                contaRepository.save(conta);

                return cobranca.copiaECola();
            } catch (Exception e) {
                logger.warn("Falha ao gerar cobrança Pix via Delfinance pro fiado #{} — caindo pro Pix estático.", id, e);
                // segue pro fallback abaixo em vez de propagar o erro pro lojista
            }
        }

        return pixService.gerarCopiaECola(
                empresa.getChavePix(),
                empresa.getNomeFantasia() != null ? empresa.getNomeFantasia() : empresa.getRazaoSocial(),
                empresa.getCidade(),
                conta.getValor(),
                "FIADO" + conta.getId()
        );
    }

    //  Chamado pelo DelfinanceWebhookController quando chega um evento
    // PIX_RECEIVED. Marca a conta como paga automaticamente, sem exigir
    // login/JWT (o webhook é uma rota pública autenticada por segredo).
    public void marcarComoPagoPorCorrelationId(String correlationId) {
        contaRepository.findByPixCorrelationId(correlationId).ifPresentOrElse(conta -> {
            if (conta.getStatus() == StatusConta.PAGO) {
                logger.info("Webhook Delfinance: fiado #{} já estava marcado como pago, ignorando.", conta.getId());
                return;
            }
            conta.setStatus(StatusConta.PAGO);
            contaRepository.save(conta);
            logger.info("Webhook Delfinance: fiado #{} marcado como pago automaticamente (correlationId={}).", conta.getId(), correlationId);
        }, () -> logger.warn("Webhook Delfinance: nenhum fiado encontrado para correlationId={}", correlationId));
    }

    public List<ContaReceber> buscarClientesParaCobrar(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        List<StatusConta> statusParaCobrar = Arrays.asList(StatusConta.PENDENTE, StatusConta.ATRASADO);

        return contaRepository.findByEmpresaIdAndStatusInAndDataProximaCobrancaLessThanEqual(
                empresaId, statusParaCobrar, hoje
        );
    }

    public ContaReceber atualizarFiado(Long id, ContaReceberDTO dto, Long empresaId) {
        ContaReceber conta = buscarContaDaEmpresa(id, empresaId);

        conta.setNomeCliente(dto.getNomeCliente());
        conta.setValor(dto.getValor());

        if (dto.getTelefoneCliente() != null) {
            conta.setTelefoneCliente(dto.getTelefoneCliente());
        }

        if (dto.getDescricao() != null) {
            conta.setDescricao(dto.getDescricao());
        }

        // Se a data for alterada, ajusta o alerta para a nova data
        if (dto.getDataVencimento() != null) {
            conta.setDataVencimento(dto.getDataVencimento());
            conta.setDataProximaCobranca(dto.getDataVencimento());
        }

        return contaRepository.save(conta);
    }

    
    private ContaReceber buscarContaDaEmpresa(Long id, Long empresaId) {
        ContaReceber conta = contaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fiado não encontrado: id=" + id));

        if (!conta.getEmpresa().getId().equals(empresaId)) {
            throw new AcessoNegadoException("Operação não permitida para esta empresa!");
        }

        return conta;
    }
}