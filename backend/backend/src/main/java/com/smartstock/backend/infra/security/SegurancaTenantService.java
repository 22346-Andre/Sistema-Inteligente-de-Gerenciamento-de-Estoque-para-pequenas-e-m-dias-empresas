package com.smartstock.backend.infra.security;

import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;


@Component
public class SegurancaTenantService {

    public Long getEmpresaIdLogada() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new RecursoNaoEncontradoException("Acesso negado: Usuário não autenticado ou token inválido.");
        }

        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    /** Lança AcessoNegadoException se o empresaId do recurso não for o da empresa logada. */
    public void validarPertenceAEmpresaLogada(Long empresaIdDoRecurso, String mensagemDeErro) {
        if (empresaIdDoRecurso == null || !empresaIdDoRecurso.equals(getEmpresaIdLogada())) {
            throw new com.smartstock.backend.exception.AcessoNegadoException(mensagemDeErro);
        }
    }
}