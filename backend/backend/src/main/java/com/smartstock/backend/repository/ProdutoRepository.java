package com.smartstock.backend.repository;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    boolean existsByNome(String nome);

    List<Produto> findByEmpresaId(Long empresaId);

    //  Traz o estoque baixo APENAS da empresa logada (Usado no Controller)
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade <= p.estoqueMinimo")
    List<Produto> findProdutosComEstoqueBaixoPorEmpresa(@Param("empresaId") Long empresaId);

    //  Traz TODOS os produtos com estoque baixo (Usado pelo Robô do E-mail, que não faz login)
    @Query("SELECT p FROM Produto p WHERE p.quantidade <= p.estoqueMinimo")
    List<Produto> findProdutosComEstoqueBaixo();

    // Busca um produto específico pelo nome, garantindo que é da empresa logada
    Optional<Produto> findByNomeAndEmpresa(String nome, Empresa empresa);
}