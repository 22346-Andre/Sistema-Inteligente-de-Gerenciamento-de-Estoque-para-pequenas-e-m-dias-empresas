package com.smartstock.backend.repository;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {

    boolean existsByNome(String nome);

    List<Produto> findByEmpresaId(Long empresaId);

    // Mesma coisa que findByEmpresaId, mas excluindo itens de "Uso Interno"
    // (mobiliário, equipamento da própria empresa) — usado por tudo que é
    // análise de capital de giro/revenda (Curva ABC, Giro de Estoque):
    // um item de uso interno nunca vai gerar faturamento, então não faz
    // sentido competir por espaço nessas análises com produto de verdade.
    // IS NULL entra na condição porque produto antigo (cadastrado antes
    // desse campo existir) tem finalidadeEstoque nulo, e nulo != 'USO_INTERNO'
    // é NULL em SQL — sem o IS NULL, esses produtos ficariam de fora sem querer.
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId " +
           "AND (p.finalidadeEstoque IS NULL OR p.finalidadeEstoque <> 'USO_INTERNO')")
    List<Produto> findByEmpresaIdParaAnaliseDeGiro(@Param("empresaId") Long empresaId);

   
    @Query("SELECT DISTINCT p.categoria FROM Produto p WHERE p.empresa.id = :empresaId " +
           "AND p.categoria IS NOT NULL AND p.categoria <> '' ORDER BY p.categoria ASC")
    List<String> findCategoriasDistintasPorEmpresa(@Param("empresaId") Long empresaId);

    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.id = :id")
    Optional<Produto> buscarComLockParaAtualizacao(@Param("id") Long id);

    // Traz o estoque baixo APENAS da empresa logada (Usado no Controller)
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade <= p.estoqueMinimo " +
           "AND (p.finalidadeEstoque IS NULL OR p.finalidadeEstoque <> 'USO_INTERNO')")
    List<Produto> findProdutosComEstoqueBaixoPorEmpresa(@Param("empresaId") Long empresaId);

    // Traz TODOS os produtos com estoque baixo (Usado pelo Robô do E-mail, que não faz login)
    @Query("SELECT p FROM Produto p WHERE p.quantidade <= p.estoqueMinimo " +
           "AND (p.finalidadeEstoque IS NULL OR p.finalidadeEstoque <> 'USO_INTERNO')")
    List<Produto> findProdutosComEstoqueBaixo();

    // Busca um produto específico pelo nome, garantindo que é da empresa logada
    Optional<Produto> findByNomeAndEmpresa(String nome, Empresa empresa);

    // Conta quantos produtos a empresa tem
    long countByEmpresaId(Long empresaId);

    //  Soma o Capital Imobilizado usando o precoCusto — só de itens de
    // revenda/matéria-prima; uso interno é ativo fixo, não capital de giro.
    @Query("SELECT COALESCE(SUM(p.precoCusto * p.quantidade), 0) FROM Produto p WHERE p.empresa.id = :empresaId " +
           "AND (p.finalidadeEstoque IS NULL OR p.finalidadeEstoque <> 'USO_INTERNO')")
    BigDecimal calcularValorTotalEstoque(@Param("empresaId") Long empresaId);

    // Ativo Não Circulante (Imobilizado) do Balanço Patrimonial — o oposto
    // do calcularValorTotalEstoque acima: soma só os itens de Uso Interno
    // (mobiliário, equipamento próprio), que são ativo fixo, não estoque de
    // giro. Sem controle de depreciação — o valor é sempre o de aquisição
    // (custo), não o valor contábil líquido; o relatório avisa essa
    // limitação no rodapé.
    @Query("SELECT COALESCE(SUM(p.precoCusto * p.quantidade), 0) FROM Produto p WHERE p.empresa.id = :empresaId " +
           "AND p.finalidadeEstoque = 'USO_INTERNO'")
    BigDecimal calcularValorImobilizado(@Param("empresaId") Long empresaId);

    // ALERTA 3: Produtos encalhados (com estoque > 0, mas sem saída recente).
    //  soma a condição "p.dataCriacao <= :dataLimite" pra não marcar
    // como morto um produto que acabou de ser cadastrado e ainda nem teve tempo
    // de vender — sem venda alguma, o prazo tem que contar a partir do cadastro,
    // não do fato de "não vendeu na janela" (que é trivialmente verdade pra
    // qualquer produto novo). Exclui uso interno: mobiliário parado não é
    // "estoque morto" no sentido comercial do painel.
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade > 0 " +
            "AND p.dataCriacao <= :dataLimite " +
            "AND (p.finalidadeEstoque IS NULL OR p.finalidadeEstoque <> 'USO_INTERNO') " +
            "AND p.id NOT IN " +
            "(SELECT m.produto.id FROM Movimentacao m WHERE m.empresa.id = :empresaId AND m.tipo = 'SAIDA' AND m.dataMovimentacao >= :dataLimite)")
    List<Produto> findProdutosEncalhados(@Param("empresaId") Long empresaId, @Param("dataLimite") LocalDateTime dataLimite);

    // Busca produtos ordenados do maior valor em stock para o menor usando precoCusto (Para Curva ABC)
    @Query("SELECT p FROM Produto p WHERE p.empresa.id = :empresaId AND p.quantidade > 0 ORDER BY (p.precoCusto * p.quantidade) DESC")
    List<Produto> findProdutosOrdenadosPorValorTotal(@Param("empresaId") Long empresaId);

    // Soma todas as unidades físicas em stock — usada no denominador do Giro
    // agregado (EstatisticasService); mesma exclusão de uso interno.
    @Query("SELECT COALESCE(SUM(p.quantidade), 0) FROM Produto p WHERE p.empresa.id = :empresaId " +
           "AND (p.finalidadeEstoque IS NULL OR p.finalidadeEstoque <> 'USO_INTERNO')")
    Integer sumQuantidadeTotalEstoque(@Param("empresaId") Long empresaId);

    // Busca produto pelo código de barras E que pertença à empresa logada
    Optional<Produto> findByCodigoBarrasAndEmpresaId(String codigoBarras, Long empresaId);

    // Mesma busca do PDV, mas travando a linha (usada em MovimentacaoService.registrarViaPDV)
    // pelo mesmo motivo do buscarComLockParaAtualizacao acima: dois PDVs baixando
    // o mesmo produto ao mesmo tempo não podem sobrescrever a baixa um do outro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.codigoBarras = :codigoBarras AND p.empresa.id = :empresaId")
    Optional<Produto> buscarPorCodigoBarrasComLockParaAtualizacao(@Param("codigoBarras") String codigoBarras, @Param("empresaId") Long empresaId);

    // Busca EM LOTE por vários códigos de barras de uma vez (evita 1 query por linha do CSV/XML)
    List<Produto> findByCodigoBarrasInAndEmpresaId(List<String> codigosBarras, Long empresaId);
    

      boolean existsByFornecedorId(Long fornecedorId);
}