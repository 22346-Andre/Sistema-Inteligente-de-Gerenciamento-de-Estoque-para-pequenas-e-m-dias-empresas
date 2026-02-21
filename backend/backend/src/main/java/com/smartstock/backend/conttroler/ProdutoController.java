package com.smartstock.backend.conttroler;

import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService service;

    @Autowired
    private ProdutoRepository produtoRepository; // Injeção necessária para o filtro

    @GetMapping
    public List<Produto> listar() {
        return service.listarTodos();
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrar(@RequestBody @Valid ProdutoDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }

    // Rota 2: O FILTRO (Traz apenas os que estão a acabar)
    @GetMapping("/criticos")
    public List<Produto> listarEstoqueCritico() {
        return produtoRepository.findProdutosComEstoqueBaixo();
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
}