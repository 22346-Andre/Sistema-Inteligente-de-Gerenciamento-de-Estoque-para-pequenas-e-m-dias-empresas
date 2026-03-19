package com.smartstock.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenService tokenService;

    public String loginComGoogle(String googleTokenString) {
        try {
            // 1. Configura o verificador com o seu Client ID real
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList("276032801929-jtq3aoqitk13pve6kegqpl55ej8sh4mb.apps.googleusercontent.com"))
                    .build();

            // 2. Verifica a autenticidade do token no servidor do Google
            GoogleIdToken idToken = verifier.verify(googleTokenString);

            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                // 3. Extrai o e-mail validado pelo Google
                String email = payload.getEmail();

                // 4. Procura esse e-mail no banco de dados
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Erro: E-mail não cadastrado no SmartStock. Crie uma conta primeiro."));


                return tokenService.gerarToken(usuario);

            } else {
                throw new RuntimeException("Token do Google inválido ou expirado.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao autenticar com o Google: " + e.getMessage());
        }
    }
}