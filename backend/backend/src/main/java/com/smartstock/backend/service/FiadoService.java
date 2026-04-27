package com.smartstock.backend.service;

import com.smartstock.backend.dto.ContaReceberDTO;
import com.smartstock.backend.model.ContaReceber;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.StatusConta;
import com.smartstock.backend.repository.ContaReceberRepository;
import com.smartstock.backend.repository.EmpresaRepository;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
public class FiadoService {

    private final ContaReceberRepository contaRepository;
    private final EmpresaRepository empresaRepository;

    public FiadoService(ContaReceberRepository contaRepository, EmpresaRepository empresaRepository) {
        this.contaRepository = contaRepository;
        this.empresaRepository = empresaRepository;
    }

    public ContaReceber registrarFiado(Long empresaId, ContaReceberDTO dto) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();

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

    public ContaReceber marcarComoPago(Long id) {
        ContaReceber conta = contaRepository.findById(id).orElseThrow();
        conta.setStatus(StatusConta.PAGO);
        return contaRepository.save(conta);
    }

    public ContaReceber adiarCobranca(Long id, int diasParaAdiar) {
        ContaReceber conta = contaRepository.findById(id).orElseThrow();
        conta.setDataProximaCobranca(LocalDate.now().plusDays(diasParaAdiar));
        return contaRepository.save(conta);
    }

    public String gerarLinkCobrancaWhatsApp(Long id) {
        ContaReceber conta = contaRepository.findById(id).orElseThrow();

        String telefoneLimpo = conta.getTelefoneCliente().replaceAll("[^0-9]", "");
        if (!telefoneLimpo.startsWith("55")) telefoneLimpo = "55" + telefoneLimpo;

        String mensagem = String.format(
                "Olá %s! Tudo bem? Passando para lembrar da sua notinha na nossa loja no valor de R$ %.2f. Podemos atualizar essa pendência hoje?",
                conta.getNomeCliente(), conta.getValor()
        );

        String textoCodificado = URLEncoder.encode(mensagem, StandardCharsets.UTF_8);
        return "https://wa.me/" + telefoneLimpo + "?text=" + textoCodificado;
    }

    public List<ContaReceber> buscarClientesParaCobrar(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        List<StatusConta> statusParaCobrar = Arrays.asList(StatusConta.PENDENTE, StatusConta.ATRASADO);

        return contaRepository.findByEmpresaIdAndStatusInAndDataProximaCobrancaLessThanEqual(
                empresaId, statusParaCobrar, hoje
        );
    }

    public ContaReceber atualizarFiado(Long id, ContaReceberDTO dto, Long empresaId) {
        ContaReceber conta = contaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fiado não encontrado!"));


        if (!conta.getEmpresa().getId().equals(empresaId)) {
            throw new RuntimeException("Operação não permitida!");
        }


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
}