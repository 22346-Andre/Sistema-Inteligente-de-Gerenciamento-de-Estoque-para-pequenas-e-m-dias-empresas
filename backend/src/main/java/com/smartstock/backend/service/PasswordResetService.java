package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;


@Service
public class PasswordResetService {

    private static final long VALIDADE_TOKEN_MINUTOS = 60;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // URL base do frontend pra montar o link do e-mail — configurável por
    // ambiente (dev/staging/prod), com o domínio atual como fallback.
    @Value("${app.frontend-url:https://frontendrepository-ebon.vercel.app}")
    private String frontendUrl;

    public void solicitarRecuperacao(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            // O token que vai por e-mail é o valor em claro (é o que o usuário precisa);
            // no banco fica só o hash, como se faz com senha — assim um vazamento do
            // banco (backup, dump, injeção de leitura) não dá para ninguém redefinir
            // senha de terceiros direto, só quem tiver o e-mail original.
            String token = UUID.randomUUID().toString();
            usuario.setResetSenhaToken(hash(token));
            usuario.setResetSenhaExpiracao(LocalDateTime.now().plusMinutes(VALIDADE_TOKEN_MINUTOS));
            usuarioRepository.save(usuario);

            String link = frontendUrl + "/redefinir-senha?token=" + token;
            emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), usuario.getNome(), link);
        });
        // Se o e-mail não existir, não faz nada — e o Controller responde a
        // mesma mensagem genérica de sucesso de qualquer jeito.
    }

    public void redefinirSenha(String token, String novaSenha) {
        if (token == null || token.isBlank()) {
            throw new RegraNegocioException("Link de recuperação inválido.");
        }

        String erroSenha = com.smartstock.backend.exception.PoliticaSenha.validar(novaSenha);
        if (erroSenha != null) {
            throw new RegraNegocioException(erroSenha);
        }

        Usuario usuario = usuarioRepository.findByResetSenhaToken(hash(token))
                .orElseThrow(() -> new RegraNegocioException("Link de recuperação inválido ou já utilizado."));

        if (usuario.getResetSenhaExpiracao() == null || LocalDateTime.now().isAfter(usuario.getResetSenhaExpiracao())) {
            throw new RegraNegocioException("Link de recuperação expirado. Solicite uma nova recuperação de senha.");
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        // Invalida o token — não pode ser usado de novo (proteção contra
        // replay caso o link vaze/seja reencaminhado por engano).
        usuario.setResetSenhaToken(null);
        usuario.setResetSenhaExpiracao(null);
        usuarioRepository.save(usuario);
    }

    private String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é garantido pela JVM (java.security.Security) — nunca deveria cair aqui.
            throw new IllegalStateException("Falha ao gerar hash do token de recuperação.", e);
        }
    }
}
