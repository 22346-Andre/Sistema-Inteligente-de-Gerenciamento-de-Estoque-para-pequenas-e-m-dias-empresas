package com.smartstock.backend.repository;

import com.smartstock.backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // Ensina o banco de dados a trazer apenas os produtos em situação crítica
    @Query("SELECT p FROM Produto p WHERE p.quantidade <= p.estoqueMinimo")
    List<Produto> findProdutosComEstoqueBaixo();
    boolean existsByNome(String nome);

}