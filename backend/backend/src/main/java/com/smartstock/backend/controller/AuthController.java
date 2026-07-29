package com.smartstock.backend.controller;

import com.smartstock.backend.dto.ConfirmarCadastroDTO;
import com.smartstock.backend.dto.EsqueciSenhaDTO;
import com.smartstock.backend.dto.GoogleLoginDTO; 
import com.smartstock.backend.dto.LoginRequest;
import com.smartstock.backend.dto.LoginResponse;
import com.smartstock.backend.dto.RedefinirSenhaDTO;
import com.smartstock.backend.dto.RegistroEmpresaDTO;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.service.AuthService; 
import com.smartstock.backend.service.EmailService;
import com.smartstock.backend.service.LoginAttemptService;
import com.smartstock.backend.service.PasswordResetService;
import com.smartstock.backend.service.RegistroService;
import com.smartstock.backend.service.TokenService;
import com.smartstock.backend.service.VerificacaoCadastroService;
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

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private VerificacaoCadastroService verificacaoCadastroService;

    @Autowired
    private EmailService emailService;

    
    @PostMapping("/registrar-empresa")
    public ResponseEntity<String> registrar(@RequestBody @Valid RegistroEmpresaDTO dto) {
        registroService.validarDisponibilidade(dto);
        String codigo = verificacaoCadastroService.iniciarCadastro(dto);

        try {
            emailService.enviarCodigoVerificacaoCadastro(dto.getEmail(), dto.getNomeDono(), codigo);
        } catch (Exception e) {
            // Envio síncrono de propósito: se falhar aqui, o e-mail informado
            // provavelmente é inválido/inexistente — não faz sentido deixar o
            // usuário esperando por um código que nunca vai chegar.
            throw new RegraNegocioException(
                    "Não foi possível enviar o código de confirmação para este e-mail. Confira se ele está correto e tente novamente."
            );
        }

        return ResponseEntity.ok("Enviamos um código de confirmação para o seu e-mail. Informe-o para concluir o cadastro.");
    }

    //  etapa 2 do cadastro — valida o código e, se estiver correto,
    // efetiva a criação da empresa e do usuário administrador.
    @PostMapping("/confirmar-cadastro")
    public ResponseEntity<String> confirmarCadastro(@RequestBody @Valid ConfirmarCadastroDTO dto) {
        RegistroEmpresaDTO dadosCadastro = verificacaoCadastroService.confirmarCadastro(dto.email(), dto.codigo());
        String mensagem = registroService.registrarNovaEmpresa(dadosCadastro);
        return ResponseEntity.ok(mensagem);
    }

    //  reenvia um novo código, caso o usuário não tenha recebido ou o
    // código anterior tenha expirado.
    @PostMapping("/reenviar-codigo-cadastro")
    public ResponseEntity<String> reenviarCodigoCadastro(@RequestBody @Valid EsqueciSenhaDTO dto) {
        String novoCodigo = verificacaoCadastroService.reenviarCodigo(dto.email());
        try {
            emailService.enviarCodigoVerificacaoCadastro(dto.email(), null, novoCodigo);
        } catch (Exception e) {
            throw new RegraNegocioException("Não foi possível reenviar o código. Tente novamente em instantes.");
        }
        return ResponseEntity.ok("Novo código enviado.");
    }

    // --- ROTA DE LOGIN NORMAL ---
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {

        
        loginAttemptService.verificarBloqueio(loginRequest.email());

        var userOptional = userRepository.findByEmail(loginRequest.email());

        if (userOptional.isEmpty() || !userOptional.get().isLoginCorrect(loginRequest, passwordEncoder)) {
            loginAttemptService.registrarFalha(loginRequest.email());
            throw new BadCredentialsException("Usuário ou senha inválidos!");
        }

        loginAttemptService.registrarSucesso(loginRequest.email());

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

   
    @PostMapping("/esqueci-senha")
    public ResponseEntity<String> esqueciSenha(@RequestBody @Valid EsqueciSenhaDTO dto) {
        passwordResetService.solicitarRecuperacao(dto.email());
        return ResponseEntity.ok("Se esse e-mail estiver cadastrado, você vai receber um link de recuperação em instantes.");
    }

   
    @PostMapping("/redefinir-senha")
    public ResponseEntity<String> redefinirSenha(@RequestBody @Valid RedefinirSenhaDTO dto) {
        passwordResetService.redefinirSenha(dto.token(), dto.novaSenha());
        return ResponseEntity.ok("Senha redefinida com sucesso! Você já pode entrar com a nova senha.");
    }
}