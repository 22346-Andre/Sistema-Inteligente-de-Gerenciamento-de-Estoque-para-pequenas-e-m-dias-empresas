package com.smartstock.backend.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaCsrfTokenRequestHandlerTest {

    @Test
    void acceptsRawTokenFromXsrfHeader() {
        var csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token");
        var request = new MockHttpServletRequest();
        request.addHeader("X-XSRF-TOKEN", "csrf-token");

        String resolvedToken = new SpaCsrfTokenRequestHandler()
                .resolveCsrfTokenValue(request, csrfToken);

        assertEquals("csrf-token", resolvedToken);
    }
}
