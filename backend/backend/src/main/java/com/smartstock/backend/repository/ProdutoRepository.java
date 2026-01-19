package com.smartstock.backend.repository;

import com.smartstock.backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    // Só isso basta. O Spring faz o resto.
}