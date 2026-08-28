package com.smartstock.backend.repository;

import com.smartstock.backend.model.MovimentoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentoCaixaRepository extends JpaRepository<MovimentoCaixa, Long> {

    List<MovimentoCaixa> findByEmpresaIdOrderByDataMovimentoDesc(Long empresaId);

    // Saldo atual = tudo que entrou menos tudo que saiu, desde sempre. É
    // isso que vira a linha "Disponibilidades" do Ativo Circulante.
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0) " +
           "FROM MovimentoCaixa m WHERE m.empresa.id = :empresaId")
    BigDecimal calcularSaldoAtual(@Param("empresaId") Long empresaId);

    // Saldo até um instante específico (pra calcular "saldo inicial" de um
    // período da DFC: saldo de tudo ANTES do início do período escolhido).
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0) " +
           "FROM MovimentoCaixa m WHERE m.empresa.id = :empresaId AND m.dataMovimento < :antesDe")
    BigDecimal calcularSaldoAntesDe(@Param("empresaId") Long empresaId, @Param("antesDe") LocalDateTime antesDe);

    // Total de entradas/saídas dentro de um período, agrupado por origem —
    // base pra montar a DFC separada por atividade (venda, fiado, despesa,
    // aporte/retirada de sócio).
    @Query("SELECT m.tipo, m.origem, COALESCE(SUM(m.valor), 0) FROM MovimentoCaixa m " +
           "WHERE m.empresa.id = :empresaId AND m.dataMovimento BETWEEN :inicio AND :fim " +
           "GROUP BY m.tipo, m.origem")
    List<Object[]> somarPorTipoEOrigemNoIntervalo(@Param("empresaId") Long empresaId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
