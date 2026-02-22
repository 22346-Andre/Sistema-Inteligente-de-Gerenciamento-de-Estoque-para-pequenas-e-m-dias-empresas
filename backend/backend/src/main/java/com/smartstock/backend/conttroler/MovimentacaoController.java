package com.smartstock.backend.conttroler;

import com.smartstock.backend.dto.MovimentacaoDTO;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.service.MovimentacaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/movimentacoes")
public class MovimentacaoController {

    @Autowired
    private MovimentacaoService service;

    @GetMapping
    public List<Movimentacao> listar() {
        // Agora ele chama o service, que vai extrair a Empresa do JWT com segurança!
        return service.listarTodas();
    }

    @PostMapping
    public ResponseEntity<Movimentacao> registrar(@RequestBody @Valid MovimentacaoDTO dto) {
        return ResponseEntity.ok(service.registrarMovimentacao(dto));
    }
}