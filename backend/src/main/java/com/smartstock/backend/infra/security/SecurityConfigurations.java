package com.smartstock.backend.infra.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfigurations {

    @Value("${rsa.public-key}")
    private RSAPublicKey publicKey;

    @Value("${rsa.private-key}")
    private RSAPrivateKey privateKey;

    
    @Value("${cors.allowed-origins:https://frontendrepository-ebon.vercel.app,https://frontendrepository-o4fgcswsg-22346-andres-projects.vercel.app,http://localhost:5173,http://localhost:3000}")
    private String[] corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .secure(true)
                .sameSite("Lax")
                .path("/"));

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/registrar-empresa").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/esqueci-senha").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/redefinir-senha").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/confirmar-cadastro").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/reenviar-codigo-cadastro").permitAll()
                        .requestMatchers(HttpMethod.GET, "/wakeup").permitAll()
                        // Health check público (é o que o Render usa pra saber se a
                        // instância está viva) — não expõe segredo nenhum, só status.
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // Demais endpoints do Actuator (ex.: /actuator/metrics) exigem
                        // usuário logado — não é ideal pra um dashboard de ops dedicado,
                        // mas evita deixar métricas do sistema totalmente públicas sem
                        // criar já uma role de admin separada.
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    // - O TRADUTOR QUE CONVERTE "perfil" PARA AUTHORITY
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("perfil"); // Puxa do campo "perfil" do seu token
        converter.setAuthorityPrefix(""); // Tira o prefixo SCOPE_

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        // Sem isso, o decoder aceita qualquer JWT assinado pela mesma chave, venha de
        // onde vier. O issuer amarra o token a "só um token que o TokenService emitiu
        // vale aqui" (defesa em profundidade; hoje só este backend usa essa chave).
        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer("smartstock-backend"));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        JWK jwk = new RSAKey.Builder(this.publicKey).privateKey(privateKey).build();
        var jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();


        configuration.setAllowedOrigins(Arrays.asList(corsAllowedOrigins));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Set-Cookie"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}