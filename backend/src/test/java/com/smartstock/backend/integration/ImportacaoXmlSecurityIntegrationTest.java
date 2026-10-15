package com.smartstock.backend.integration;

import com.smartstock.backend.service.ImportacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportacaoXmlSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ImportacaoService importacaoService;

    @Test
    void xmlComDoctypeEEntidadeExternaDeveSerRejeitado() {
        String xml = """
                <!DOCTYPE nota [
                  <!ENTITY externa SYSTEM "file:///etc/passwd">
                ]>
                <NFe>&externa;</NFe>
                """;
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "nota.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> importacaoService.extrairDadosDoXmlSefaz(arquivo));
    }
}
