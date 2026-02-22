package com.smartstock.backend.conttroler;

import com.smartstock.backend.dto.LoginRequest;
import com.smartstock.backend.dto.LoginResponse;
import com.smartstock.backend.repository.EmpresaRepository; // <-- Import novo
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

import java.time.LocalDateTime; // <-- Import novo

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository userRepository;

    @Autowired
    private EmpresaRepository empresaRepository; // <-- Injetando para podermos salvar a data!

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {

        // 1. Busca o usuário no banco
        var userOptional = userRepository.findByEmail(loginRequest.email());

        // 2. Verificar a senha
        if (userOptional.isEmpty() || !userOptional.get().isLoginCorrect(loginRequest, passwordEncoder)) {
            throw new BadCredentialsException("Usuário ou senha inválidos!");
        }

        var user = userOptional.get();

        //ATUALIZAR ÚLTIMO ACESSO ---
        if (user.getEmpresa() != null) {
            user.getEmpresa().setUltimoAcesso(LocalDateTime.now());
            empresaRepository.save(user.getEmpresa()); // Salva a hora exata do login no banco
        }

        // 3. Delegamos a criação do Token para o TokenService
        var jwtValue = tokenService.gerarToken(user);

        var expiresIn = 3600L; // 1 hora de validade

        return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
    }
}