package com.smartstock.backend.controller;

import com.smartstock.backend.dto.EstatisticasDTO;
import com.smartstock.backend.service.EstatisticasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/estatisticas")
public class EstatisticasController {

    @Autowired
    private EstatisticasService service;

    //  Apenas gestores podem aceder a dados financeiros profundos
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public EstatisticasDTO obterEstatisticas() {
        return service.gerarEstatisticas();
    }
}
