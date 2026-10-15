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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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

    @GetMapping("/csrf")
    public ResponseEntity<String> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(csrfToken.getToken());
    }

    
    @PostMapping("/registrar-empresa")
    public ResponseEntity<String> registrar(@RequestBody @Valid RegistroEmpresaDTO dto, HttpServletRequest request) {
        loginAttemptService.consumirLimite("registrar-empresa|" + extrairIpCliente(request), 5);
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
    public ResponseEntity<String> reenviarCodigoCadastro(@RequestBody @Valid EsqueciSenhaDTO dto, HttpServletRequest request) {
        loginAttemptService.consumirLimite("reenviar-codigo|" + extrairIpCliente(request), 5);
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
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {

        String ip = extrairIpCliente(request);

        loginAttemptService.verificarBloqueio(loginRequest.email(), ip);

        var userOptional = userRepository.findByEmail(loginRequest.email());

        if (userOptional.isEmpty() || !userOptional.get().isLoginCorrect(loginRequest, passwordEncoder)) {
            loginAttemptService.registrarFalha(loginRequest.email(), ip);
            throw new BadCredentialsException("Usuário ou senha inválidos!");
        }

        loginAttemptService.registrarSucesso(loginRequest.email(), ip);

        var user = userOptional.get();

        if (user.getEmpresa() != null) {
            user.getEmpresa().setUltimoAcesso(LocalDateTime.now());
            empresaRepository.save(user.getEmpresa());
        }

        var jwtValue = tokenService.gerarToken(user);
        var expiresIn = 3600L;

        definirCookieAcesso(response, jwtValue, expiresIn);
        return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
    }


    @PostMapping("/login/google")
    public ResponseEntity<?> loginComGoogle(@RequestBody GoogleLoginDTO dto,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        // O login por senha tem seu próprio limite (email+IP, email, IP); aqui só cabe
        // o de IP, já que não há "e-mail digitado" antes de verificar o token do Google.
        String ip = extrairIpCliente(request);
        loginAttemptService.verificarBloqueioIp(ip);
        try {
            // O AuthService vai lá no Google, verifica o token e fabrica o nosso JWT
            String jwtValue = authService.loginComGoogle(dto.getToken());
            var expiresIn = 3600L;

            definirCookieAcesso(response, jwtValue, expiresIn);
            // Devolvemos exatamente no mesmo formato do login normal!
            return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
        } catch (RuntimeException e) {
            loginAttemptService.registrarFalhaIp(ip);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    private void definirCookieAcesso(HttpServletResponse response, String token, long expiresIn) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(expiresIn))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

   
    @PostMapping("/esqueci-senha")
    public ResponseEntity<String> esqueciSenha(@RequestBody @Valid EsqueciSenhaDTO dto, HttpServletRequest request) {
        loginAttemptService.consumirLimite("esqueci-senha|" + extrairIpCliente(request), 5);
        passwordResetService.solicitarRecuperacao(dto.email());
        return ResponseEntity.ok("Se esse e-mail estiver cadastrado, você vai receber um link de recuperação em instantes.");
    }

   
    @PostMapping("/redefinir-senha")
    public ResponseEntity<String> redefinirSenha(@RequestBody @Valid RedefinirSenhaDTO dto) {
        passwordResetService.redefinirSenha(dto.token(), dto.novaSenha());
        return ResponseEntity.ok("Senha redefinida com sucesso! Você já pode entrar com a nova senha.");
    }

    /**
     * Atrás de um load balancer/proxy (Render, Railway etc.), o IP que chega
     * na conexão TCP é o do proxy, não o do cliente real — por isso é
     * preciso ler o cabeçalho X-Forwarded-For primeiro. Esse cabeçalho pode
     * ter uma lista "cliente, proxy1, proxy2"; o primeiro valor é o mais
     * próximo do cliente original.
     *
     * ATENÇÃO se algum dia isso for exposto sem estar atrás do proxy da
     * plataforma (Render/Railway): X-Forwarded-For é um header que QUALQUER
     * requisição pode mandar forjado. Hoje isso é seguro porque o único
     * caminho de entrada é o proxy da plataforma de hospedagem, que
     * sobrescreve esse header. Se o app um dia for exposto direto (sem
     * proxy na frente), esse método precisa ser revisto.
     */
    private String extrairIpCliente(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}