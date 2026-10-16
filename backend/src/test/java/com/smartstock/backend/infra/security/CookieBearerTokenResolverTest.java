package com.smartstock.backend.infra.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @Test
    void resolvesTokenFromAccessCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertEquals("cookie-token", resolver.resolve(request));
    }

    @Test
    void authorizationHeaderTakesPrecedenceOverCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertEquals("header-token", resolver.resolve(request));
    }

    @Test
    void returnsNullWhenNoTokenIsPresent() {
        assertNull(resolver.resolve(new MockHttpServletRequest()));
    }

    @Test
    void ignoresAuthorizationHeaderAndCookieOnPublicRoutes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/auth/login");
        request.addHeader("Authorization", "Bearer invalid-token");
        request.setCookies(new Cookie("access_token", "invalid-cookie"));

        assertNull(resolver.resolve(request));
    }

    @Test
    void resolvesAccessCookieOnProtectedRoutes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/produtos");
        request.setCookies(new Cookie("access_token", "cookie-token"));

        assertEquals("cookie-token", resolver.resolve(request));
    }
}
