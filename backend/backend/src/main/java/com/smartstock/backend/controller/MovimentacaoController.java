package com.smartstock.backend.controller;

import com.smartstock.backend.dto.MovimentacaoPdvDTO;
import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.service.MovimentacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/movimentacoes")
public class MovimentacaoController {

    @Autowired
    private MovimentacaoService service;

    //  Dashboard e Relatórios lerem o histórico
    @GetMapping
    public List<Movimentacao> listar() {
        return service.listarTodas();
    }

    //
    @PostMapping("/pdv")
    public ResponseEntity<?> registrarPDV(@RequestBody MovimentacaoPdvDTO dto) {
        try {
            Movimentacao movimentacao = service.registrarViaPDV(dto);
            return ResponseEntity.ok(movimentacao);
        } catch (RecursoNaoEncontradoException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MensagemErroDTO(e.getMessage()));
        } catch (AcessoNegadoException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MensagemErroDTO(e.getMessage()));
        } catch (RegraNegocioException e) {
            return ResponseEntity.badRequest().body(new MensagemErroDTO(e.getMessage()));
        } catch (Exception e) {
            // Qualquer outro erro inesperado (não mapeado nas exceções específicas acima)
            return ResponseEntity.badRequest().body(new MensagemErroDTO(e.getMessage()));
        }
    }

    // Classe auxiliar apenas para mandar o erro formatado para o React ler
    static class MensagemErroDTO {
        public String message;
        public MensagemErroDTO(String message) { this.message = message; }
    }

    @GetMapping("/produto/{produtoId}")
    public ResponseEntity<List<Movimentacao>> listarPorProduto(@PathVariable Long produtoId) {
        return ResponseEntity.ok(service.listarPorProduto(produtoId));
    }
}