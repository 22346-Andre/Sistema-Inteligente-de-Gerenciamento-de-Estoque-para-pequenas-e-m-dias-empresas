package com.smartstock.backend.controller;

import com.smartstock.backend.dto.EmpresaDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/empresas")
public class EmpresaController {

    @Autowired
    private EmpresaService service;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    // --- ROTAS DO SUPORTE (SUPER_ADMIN) ---
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping
    public List<Empresa> listar() {
        return service.listarTodas();
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Empresa> cadastrar(@RequestBody @Valid EmpresaDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }


    // --- ROTAS DA EMPRESA LOGADA (Para a tela de Configurações do React) ---

    // 1. GET: Busca os dados da empresa do usuário logado (Você tinha apagado este sem querer!)
    @GetMapping("/minha-empresa")
    public ResponseEntity<Empresa> buscarMinhaEmpresa() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String emailLogado = authentication.getName();

        Usuario usuarioLogado = usuarioRepository.findByEmail(emailLogado)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        return ResponseEntity.ok(usuarioLogado.getEmpresa());
    }

    // 2. PUT: Atualiza os dados da empresa do usuário logado
    @PutMapping("/minha-empresa")
    public ResponseEntity<Empresa> atualizarMinhaEmpresa(@RequestBody Empresa dtoAtualizacao) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String emailLogado = authentication.getName();

        Usuario usuarioLogado = usuarioRepository.findByEmail(emailLogado)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Empresa minhaEmpresa = usuarioLogado.getEmpresa();

        // Atualiza os dados que vieram do React
        minhaEmpresa.setRazaoSocial(dtoAtualizacao.getRazaoSocial());
        minhaEmpresa.setNomeFantasia(dtoAtualizacao.getNomeFantasia());
        minhaEmpresa.setEmailContato(dtoAtualizacao.getEmailContato());

        // Atualiza os campos novos
        minhaEmpresa.setTelefone(dtoAtualizacao.getTelefone());
        minhaEmpresa.setEndereco(dtoAtualizacao.getEndereco());
        minhaEmpresa.setCidade(dtoAtualizacao.getCidade());
        minhaEmpresa.setEstado(dtoAtualizacao.getEstado());

        // Salva a alteração no banco de dados
        Empresa empresaAtualizada = empresaRepository.save(minhaEmpresa);

        return ResponseEntity.ok(empresaAtualizada);
    }
}