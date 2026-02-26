package com.smartstock.backend.controller;

import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.service.MovimentacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/movimentacoes")
public class MovimentacaoController {

    @Autowired
    private MovimentacaoService service;

    // Rota EXCLUSIVA para o Dashboard e Relatórios lerem o histórico
    @GetMapping
    public List<Movimentacao> listar() {
        // O service já extrai a Empresa do JWT com segurança e traz só o que é dela!
        return service.listarTodas();
    }
}