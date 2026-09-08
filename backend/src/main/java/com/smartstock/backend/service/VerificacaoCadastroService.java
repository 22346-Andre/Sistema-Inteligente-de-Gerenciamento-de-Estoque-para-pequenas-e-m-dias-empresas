package com.smartstock.backend.service;

import com.smartstock.backend.dto.RegistroEmpresaDTO;
import com.smartstock.backend.exception.RegraNegocioException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  garante que ninguém consiga terminar o cadastro com um e-mail que
 * não existe/não é dele, evitando que os e-mails de alerta do sistema (estoque
 * baixo, recuperação de senha, etc.) sejam disparados pra um destino inválido.
 *
 * Fluxo:
 * 1. iniciarCadastro(dto) — valida e-mail/CNPJ disponíveis, gera um código de
 *    6 dígitos, guarda o cadastro (ainda não persistido no banco) em memória
 *    por até 15 minutos, e devolve o código pro AuthController mandar por
 *    e-mail.
 * 2. confirmarCadastro(email, codigo) — valida o código informado. Se bater,
 *    devolve o DTO original pro AuthController finalmente chamar
 *    RegistroService.registrarNovaEmpresa(dto) e criar a empresa/usuário de
 *    verdade no banco.
 *
 * IMPORTANTE: a empresa e o usuário SÓ são criados no banco depois da
 * confirmação — evita ficar com "contas fantasmas" de gente que nunca validou
 * o e-mail.
 *
 * Mesma limitação conhecida do LoginAttemptService: armazenamento em memória,
 * válido para uma única instância do backend (cenário atual).
 */
@Service
public class VerificacaoCadastroService {

    private static final long VALIDADE_CODIGO_MINUTOS = 15;
    private static final int MAX_TENTATIVAS_CODIGO = 5;

    private static class CadastroPendente {
        RegistroEmpresaDTO dto;
        String codigo;
        LocalDateTime expiracao;
        int tentativas;
    }

    private final Map<String, CadastroPendente> pendentes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Gera e guarda o código; devolve o código gerado pra quem chamar mandar por e-mail. */
    public String iniciarCadastro(RegistroEmpresaDTO dto) {
        String codigo = String.format("%06d", random.nextInt(1_000_000));

        CadastroPendente pendente = new CadastroPendente();
        pendente.dto = dto;
        pendente.codigo = codigo;
        pendente.expiracao = LocalDateTime.now().plusMinutes(VALIDADE_CODIGO_MINUTOS);
        pendente.tentativas = 0;

        pendentes.put(normalizar(dto.getEmail()), pendente);
        return codigo;
    }

    /** Gera um novo código para um cadastro já iniciado (reenvio). */
    public String reenviarCodigo(String email) {
        CadastroPendente pendente = pendentes.get(normalizar(email));
        if (pendente == null) {
            throw new RegraNegocioException("Nenhum cadastro pendente encontrado para este e-mail. Preencha o formulário novamente.");
        }
        String novoCodigo = String.format("%06d", random.nextInt(1_000_000));
        pendente.codigo = novoCodigo;
        pendente.expiracao = LocalDateTime.now().plusMinutes(VALIDADE_CODIGO_MINUTOS);
        pendente.tentativas = 0;
        return novoCodigo;
    }

    /** Valida o código e devolve os dados do cadastro pendente pra ser efetivado no banco. */
    public RegistroEmpresaDTO confirmarCadastro(String email, String codigo) {
        String chave = normalizar(email);
        CadastroPendente pendente = pendentes.get(chave);

        if (pendente == null) {
            throw new RegraNegocioException("Nenhum cadastro pendente encontrado para este e-mail. Preencha o formulário novamente.");
        }

        if (LocalDateTime.now().isAfter(pendente.expiracao)) {
            pendentes.remove(chave);
            throw new RegraNegocioException("Código expirado. Solicite um novo código.");
        }

        pendente.tentativas++;
        if (pendente.tentativas > MAX_TENTATIVAS_CODIGO) {
            pendentes.remove(chave);
            throw new RegraNegocioException("Muitas tentativas incorretas. Solicite um novo código.");
        }

        if (!pendente.codigo.equals(codigo == null ? "" : codigo.trim())) {
            throw new RegraNegocioException("Código incorreto. Confira e tente novamente.");
        }

        pendentes.remove(chave);
        return pendente.dto;
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
