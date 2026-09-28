package com.smartstock.backend.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O segredo do webhook nunca pode sair em JSON (ex.: /usuarios/me, /empresas/minha-empresa). */
class EmpresaJsonTest {

    @Test
    void webhookSecretNaoEhSerializado() throws Exception {
        Empresa empresa = new Empresa();
        empresa.setCnpj("11222333000181");
        empresa.setRazaoSocial("Empresa A LTDA");
        empresa.setWebhookSecret("segredo-super-secreto");

        String json = new ObjectMapper().writeValueAsString(empresa);

        assertFalse(json.contains("webhookSecret"), "O campo não pode aparecer no JSON");
        assertFalse(json.contains("segredo-super-secreto"), "O valor não pode aparecer no JSON");
    }

    @Test
    void usuarioNaoVazaSegredoDaEmpresaVinculada() throws Exception {
        Empresa empresa = new Empresa();
        empresa.setCnpj("11222333000181");
        empresa.setRazaoSocial("Empresa A LTDA");
        empresa.setWebhookSecret("segredo-super-secreto");
        Usuario usuario = new Usuario();
        usuario.setNome("Caixa");
        usuario.setEmail("caixa@empresa.com");
        usuario.setEmpresa(empresa);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(usuario);

        assertTrue(json.contains("Empresa A LTDA"));
        assertFalse(json.contains("segredo-super-secreto"));
    }
}
