package com.smartstock.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenService tokenService;

    
    @Value("${google.oauth.client-id}")
    private String googleClientId;

    public String loginComGoogle(String googleTokenString) {
        try {
            // 1. Configura o verificador com o Client ID vindo de configuração
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            // 2. Verifica a autenticidade do token no servidor do Google
            GoogleIdToken idToken = verifier.verify(googleTokenString);

            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                // 3. Extrai o e-mail validado pelo Google
                String email = payload.getEmail();

                // 4. Procura esse e-mail no banco de dados
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new RegraNegocioException("Erro: E-mail não cadastrado no SmartStock. Crie uma conta primeiro."));


                return tokenService.gerarToken(usuario);

            } else {
                throw new RegraNegocioException("Token do Google inválido ou expirado.");
            }
        } catch (RegraNegocioException e) {
           
            throw e;
        } catch (Exception e) {
            // Falha inesperada (rede, verificação do token do Google, etc.) — essa
            // sim vira uma RegraNegocioException com contexto, em vez de uma
            // RuntimeException crua e sem tipo semântico.
            throw new RegraNegocioException("Falha ao autenticar com o Google: " + e.getMessage());
        }
    }
}