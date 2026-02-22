package com.smartstock.backend.conttroler;

import com.smartstock.backend.dto.EmpresaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/empresas")
public class EmpresaController {

    @Autowired
    private EmpresaService service;

    @GetMapping
    public List<Empresa> listar() {
        return service.listarTodas();
    }

    @PostMapping
    public ResponseEntity<Empresa> cadastrar(@RequestBody @Valid EmpresaDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }
}