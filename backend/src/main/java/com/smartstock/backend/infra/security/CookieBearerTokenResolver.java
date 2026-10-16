package com.smartstock.backend.infra.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Set;

public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "/auth/login", "/auth/login/google", "/auth/logout", "/auth/csrf",
            "/auth/registrar-empresa", "/auth/confirmar-cadastro",
            "/auth/reenviar-codigo-cadastro", "/auth/esqueci-senha", "/auth/redefinir-senha");

    private final BearerTokenResolver authorizationHeaderResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (PUBLIC_ROUTES.contains(request.getServletPath())) {
            return null;
        }

        String authorizationToken = authorizationHeaderResolver.resolve(request);
        if (authorizationToken != null) {
            return authorizationToken;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
