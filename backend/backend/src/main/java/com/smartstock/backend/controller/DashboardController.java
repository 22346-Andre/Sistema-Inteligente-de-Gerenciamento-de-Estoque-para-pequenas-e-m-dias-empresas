package com.smartstock.backend.controller;

import com.smartstock.backend.dto.DashboardDTO;
import com.smartstock.backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService service;

    @GetMapping("/resumo")
    public DashboardDTO obterResumo() {
        return service.obterResumoDashboard();
    }

    @GetMapping("/grafico")
    public java.util.List<com.smartstock.backend.dto.GraficoDTO> obterGrafico() {
        return service.obterDadosGrafico();
    }
}