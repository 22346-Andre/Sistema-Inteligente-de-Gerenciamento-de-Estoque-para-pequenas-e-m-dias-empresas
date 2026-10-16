package com.smartstock.backend.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaCsrfTokenRequestHandlerTest {

    @Test
    void tokenExpostoNaRequisicaoEhOCru() {
        var csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "raw-token");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        new SpaCsrfTokenRequestHandler().handle(request, response, () -> csrfToken);

        var exposedToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        assertEquals("raw-token", exposedToken.getToken());
    }
}
