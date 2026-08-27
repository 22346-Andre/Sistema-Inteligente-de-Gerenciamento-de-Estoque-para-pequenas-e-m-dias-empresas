package com.smartstock.backend.repository;

import com.smartstock.backend.model.Despesa;
import com.smartstock.backend.model.StatusConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    List<Despesa> findByEmpresaIdOrderByDataVencimentoAsc(Long empresaId);

    List<Despesa> findByEmpresaIdAndStatus(Long empresaId, StatusConta status);

    // Passivo Circulante do Balanço Patrimonial: soma de tudo que está em
    // aberto (PENDENTE ou ATRASADO) — dívida que a empresa ainda tem que
    // pagar, independente de quando vence.
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d " +
           "WHERE d.empresa.id = :empresaId AND d.status IN ('PENDENTE', 'ATRASADO')")
    BigDecimal somarContasAPagarEmAberto(@Param("empresaId") Long empresaId);

    // Total de despesas efetivamente pagas dentro de um período — é isso
    // que entra no DRE como "Despesas Operacionais", não o valor de
    // despesas em aberto (que ainda não afetou o caixa/resultado do período).
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d " +
           "WHERE d.empresa.id = :empresaId AND d.dataPagamento IS NOT NULL " +
           "AND d.dataPagamento BETWEEN :inicio AND :fim")
    BigDecimal somarDespesasPagasNoIntervalo(@Param("empresaId") Long empresaId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
