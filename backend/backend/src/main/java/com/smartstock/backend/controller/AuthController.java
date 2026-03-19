package com.smartstock.backend.controller;

import com.smartstock.backend.dto.GoogleLoginDTO; // 🚨 Não esqueça de importar o DTO novo!
import com.smartstock.backend.dto.LoginRequest;
import com.smartstock.backend.dto.LoginResponse;
import com.smartstock.backend.dto.RegistroEmpresaDTO;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.service.AuthService; // 🚨 Importando o novo serviço do Google
import com.smartstock.backend.service.RegistroService;
import com.smartstock.backend.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository userRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RegistroService registroService;

    @Autowired
    private AuthService authService;

    // --- ROTA PÚBLICA DE CADASTRO DA EMPRESA E DO DONO ---
    @PostMapping("/registrar-empresa")
    public ResponseEntity<String> registrar(@RequestBody @Valid RegistroEmpresaDTO dto) {
        try {
            String mensagem = registroService.registrarNovaEmpresa(dto);
            return ResponseEntity.ok(mensagem);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- ROTA DE LOGIN NORMAL ---
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {

        var userOptional = userRepository.findByEmail(loginRequest.email());

        if (userOptional.isEmpty() || !userOptional.get().isLoginCorrect(loginRequest, passwordEncoder)) {
            throw new BadCredentialsException("Usuário ou senha inválidos!");
        }

        var user = userOptional.get();

        if (user.getEmpresa() != null) {
            user.getEmpresa().setUltimoAcesso(LocalDateTime.now());
            empresaRepository.save(user.getEmpresa());
        }

        var jwtValue = tokenService.gerarToken(user);
        var expiresIn = 3600L;

        return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
    }

    
    @PostMapping("/login/google")
    public ResponseEntity<?> loginComGoogle(@RequestBody GoogleLoginDTO dto) {
        try {
            // O AuthService vai lá no Google, verifica o token e fabrica o nosso JWT
            String jwtValue = authService.loginComGoogle(dto.getToken());
            var expiresIn = 3600L;

            // Devolvemos exatamente no mesmo formato do login normal!
            return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}