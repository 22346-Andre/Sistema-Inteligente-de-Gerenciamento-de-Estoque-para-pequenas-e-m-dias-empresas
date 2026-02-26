package com.smartstock.backend.service;

import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.repository.MovimentacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MovimentacaoService {

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    // --- MÉTODO AUXILIAR DO JWT ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Movimentacao> listarTodas() {
        // Retorna só o histórico da empresa logada para montar o Dashboard!
        return movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(getEmpresaIdLogada());
    }
}