package com.smartstock.backend.controller;

import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService service;

    @GetMapping
    public List<Produto> listar() {
        return service.listarTodos();
    }


    @GetMapping("/{id}")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        Produto produto = service.buscarPorId(id);
        return ResponseEntity.ok(produto);
    }

    // --- A ROTA DE BUSCA AVANÇADA ---
    @GetMapping("/busca-avancada")
    public ResponseEntity<List<Produto>> buscarProdutosAvancado(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) BigDecimal precoMin,
            @RequestParam(required = false) BigDecimal precoMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio) {

        List<Produto> produtos = service.buscaAvancada(categoria, precoMin, precoMax, dataInicio);
        return ResponseEntity.ok(produtos);
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrar(@RequestBody @Valid ProdutoDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }

    // Traz apenas os que estão a acabar, filtrado por empresa
    @GetMapping("/criticos")
    public List<Produto> listarEstoqueCritico() {
        return service.listarEstoqueCritico();
    }

    // Rota 3: ATUALIZAR UM PRODUTO (PUT)
    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dto) {
        Produto produtoAtualizado = service.atualizar(id, dto);
        return ResponseEntity.ok(produtoAtualizado);
    }

    // Rota 4: APAGAR UM PRODUTO (DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

    // Rota 5: ADICIONAR UM NOVO LOTE (ENTRADA DE STOCK)
    @PostMapping("/{id}/lotes")
    public ResponseEntity<Produto> darEntradaLote(@PathVariable Long id, @RequestBody com.smartstock.backend.dto.LoteDTO dto) {
        Produto produtoAtualizado = service.adicionarLote(id, dto);
        return ResponseEntity.ok(produtoAtualizado);
    }

    // Rota 6: REGISTAR VENDA/BAIXA (SAÍDA DE STOCK COM FEFO)
    @PostMapping("/{id}/saida")
    public ResponseEntity<String> registarSaida(@PathVariable Long id, @RequestBody com.smartstock.backend.dto.SaidaDTO dto) {
        service.registrarSaida(id, dto.getQuantidadeDesejada());
        return ResponseEntity.ok("Saída de " + dto.getQuantidadeDesejada() + " unidades registada com sucesso! Lotes atualizados via FEFO.");
    }
}