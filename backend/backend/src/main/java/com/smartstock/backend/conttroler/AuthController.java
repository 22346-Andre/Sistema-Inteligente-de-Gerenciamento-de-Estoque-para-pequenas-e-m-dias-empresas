package com.smartstock.backend.controller;

import com.smartstock.backend.dto.LoginRequest;
import com.smartstock.backend.dto.LoginResponse;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;


    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {

        // 1. Buscamos o usuário no banco
        var userOptional = userRepository.findByEmail(loginRequest.email());

        // 2. Verificar a senha
        if (userOptional.isEmpty() || !userOptional.get().isLoginCorrect(loginRequest, passwordEncoder)) {
            throw new BadCredentialsException("Usuário ou senha inválidos!");
        }

        var user = userOptional.get();

        // 3. Delegamos a criação do Token para o TokenService
        var jwtValue = tokenService.gerarToken(user);

        var expiresIn = 3600L; // 1 hora de validade, igual configuramos no TokenService

        return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
    }
}