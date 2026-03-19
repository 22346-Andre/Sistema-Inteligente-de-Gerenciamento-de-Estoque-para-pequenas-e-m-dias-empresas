package com.smartstock.backend.repository;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {

    boolean existsByNome(String nome);

    List<Produto> findByEmpresaId(Long empresaId);

    // Traz o estoque baixo APENAS da empresa logada (Usado no Controller)
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade <= p.estoqueMinimo")
    List<Produto> findProdutosComEstoqueBaixoPorEmpresa(@Param("empresaId") Long empresaId);

    // Traz TODOS os produtos com estoque baixo (Usado pelo Robô do E-mail, que não faz login)
    @Query("SELECT p FROM Produto p WHERE p.quantidade <= p.estoqueMinimo")
    List<Produto> findProdutosComEstoqueBaixo();

    // Busca um produto específico pelo nome, garantindo que é da empresa logada
    Optional<Produto> findByNomeAndEmpresa(String nome, Empresa empresa);

    // Conta quantos produtos a empresa tem
    long countByEmpresaId(Long empresaId);

    //  Soma o Capital Imobilizado usando o precoCusto
    @Query("SELECT COALESCE(SUM(p.precoCusto * p.quantidade), 0) FROM Produto p WHERE p.empresa.id = :empresaId")
    BigDecimal calcularValorTotalEstoque(@Param("empresaId") Long empresaId);

    // ALERTA 3: Produtos encalhados (com estoque > 0, mas sem saída recente)
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade > 0 AND p.id NOT IN " +
            "(SELECT m.produto.id FROM Movimentacao m WHERE m.empresa.id = :empresaId AND m.tipo = 'SAIDA' AND m.dataMovimentacao >= :dataLimite)")
    List<Produto> findProdutosEncalhados(@Param("empresaId") Long empresaId, @Param("dataLimite") LocalDateTime dataLimite);

    // Busca produtos ordenados do maior valor em stock para o menor usando precoCusto (Para Curva ABC)
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade > 0 ORDER BY (p.precoCusto * p.quantidade) DESC")
    List<Produto> findProdutosOrdenadosPorValorTotal(@Param("empresaId") Long empresaId);

    // Soma todas as unidades físicas em stock
    @Query("SELECT COALESCE(SUM(p.quantidade), 0) FROM Produto p WHERE p.empresa.id = :empresaId")
    Integer sumQuantidadeTotalEstoque(@Param("empresaId") Long empresaId);

    // Busca produto pelo código de barras E que pertença à empresa logada
    Optional<Produto> findByCodigoBarrasAndEmpresaId(String codigoBarras, Long empresaId);

}