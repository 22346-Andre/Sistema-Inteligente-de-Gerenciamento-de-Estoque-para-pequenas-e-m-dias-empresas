package com.smartstock.backend.service;

import com.smartstock.backend.dto.LoginDTO;
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
        Instant now = Instant.now();
        long expiry = 3600L; // 1 hora

        var claims = JwtClaimsSet.builder()
                .issuer("smartstock-backend")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .subject(usuario.getEmail())
                .claim("id", usuario.getId())
                .claim("perfil", usuario.getPerfil())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}