package com.smartstock.backend.controller;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import com.smartstock.backend.service.CompraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/compras")
public class CompraController {

    @Autowired
    private CompraService service;

    @GetMapping("/sugestoes-whatsapp")
    public List<SugestaoCompraDTO> obterSugestoes() {
        return service.gerarSugestoesDeCompraWhatsapp();
    }
}
