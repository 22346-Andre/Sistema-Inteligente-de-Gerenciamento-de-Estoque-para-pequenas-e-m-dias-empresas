package com.smartstock.backend.repository;

import com.smartstock.backend.model.Movimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    // Traz de todas as empresas)
    List<Movimentacao> findAllByOrderByDataMovimentacaoDesc();

    //  Busca SÓ as movimentações da empresa logada, ordenadas por data
    List<Movimentacao> findByEmpresaIdOrderByDataMovimentacaoDesc(Long empresaId);

    // Pega só as 5 últimas movimentações para a listinha inferior do Dashboard
    List<Movimentacao> findTop5ByEmpresaIdOrderByDataMovimentacaoDesc(Long empresaId);

    // Busca as movimentações da empresa a partir de uma data específica
    @org.springframework.data.jpa.repository.Query("SELECT m FROM Movimentacao m WHERE m.empresa.id = :empresaId AND m.dataMovimentacao >= :dataInicio")
    List<Movimentacao> findMovimentacoesUltimosDias(Long empresaId, java.time.LocalDateTime dataInicio);

    // Soma o total de itens vendidos/saídas nos últimos dias (Para o Giro de Estoque)
    @Query("SELECT COALESCE(SUM(m.quantidade), 0) FROM Movimentacao m WHERE m.empresa.id = :empresaId AND m.tipo = 'SAIDA' AND m.dataMovimentacao >= :dataInicio")
    Integer sumSaidasUltimosDias(@Param("empresaId") Long empresaId, @Param("dataInicio") java.time.LocalDateTime dataInicio);


    List<Movimentacao> findByProdutoIdOrderByDataMovimentacaoDesc(Long produtoId);

}