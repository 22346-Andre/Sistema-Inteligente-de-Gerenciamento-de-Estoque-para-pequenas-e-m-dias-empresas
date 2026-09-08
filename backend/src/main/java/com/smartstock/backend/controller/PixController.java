package com.smartstock.backend.controller;

import com.smartstock.backend.dto.GerarPixDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import com.smartstock.backend.service.PixService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/pix")
public class PixController {

    @Autowired
    private PixService pixService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Empresa getEmpresaLogada() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        return usuario.getEmpresa();
    }

    // POST /pix/gerar — usado no PDV pra cobrar o valor do carrinho na hora,
    // e em qualquer outra tela que precise de uma cobrança avulsa.
    @PostMapping("/gerar")
    public ResponseEntity<Map<String, String>> gerar(@RequestBody @Valid GerarPixDTO dto) {
        Empresa empresa = getEmpresaLogada();

        if (empresa.getChavePix() == null || empresa.getChavePix().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Nenhuma chave PIX cadastrada. Configure em Configurações > Empresa antes de gerar cobranças."
            ));
        }

        String copiaECola = pixService.gerarCopiaECola(
                empresa.getChavePix(),
                empresa.getNomeFantasia() != null ? empresa.getNomeFantasia() : empresa.getRazaoSocial(),
                empresa.getCidade(),
                dto.valor(),
                dto.identificador()
        );

        return ResponseEntity.ok(Map.of("copiaECola", copiaECola));
    }
}
