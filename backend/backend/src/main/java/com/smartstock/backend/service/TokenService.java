package com.smartstock.backend.service;

import com.smartstock.backend.model.Usuario;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;

    public TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String gerarToken(Usuario usuario) {

        // --- INÍCIO DO DEBUG (Agora no lugar certo, DENTRO do método!) ---
        System.out.println("🚨 [DEBUG LOGIN] Fabricando token para: " + usuario.getEmail());
        if (usuario.getEmpresa() != null) {
            System.out.println("🚨 [DEBUG LOGIN] Empresa encontrada! ID: " + usuario.getEmpresa().getId());
        } else {
            System.out.println("🚨 [DEBUG LOGIN] ALERTA VERMELHO! A empresa chegou NULL no TokenService!");
        }
        // --- FIM DO DEBUG ---

        Instant now = Instant.now();
        long expiry = 3600L; // 1 hora

        var claims = JwtClaimsSet.builder()
                .issuer("smartstock-backend")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .subject(usuario.getEmail())
                .claim("id", usuario.getId())
                .claim("perfil", usuario.getPerfil())
                // O carimbo da empresa no token
                .claim("empresaId", usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}