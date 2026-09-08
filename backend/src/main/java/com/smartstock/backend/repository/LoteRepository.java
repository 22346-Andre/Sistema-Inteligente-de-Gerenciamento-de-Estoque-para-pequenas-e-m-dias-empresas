package com.smartstock.backend.repository;

import com.smartstock.backend.model.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {

    //  FEFO/FIFO
    // Pega os lotes com saldo. Prioridade: 1) quem tem validade cadastrada
    // vende primeiro, do que vence mais cedo pro que vence mais tarde;
    // 2) só depois de esgotar os lotes com validade, entra quem não tem
    // validade (produto que não vence — ex.: peça de oficina), esses saem
    // por ordem de chegada (FIFO).
    // Sem esse CASE, MySQL trata NULL como "menor valor" no ASC e venderia
    // os sem validade ANTES dos que já estão perto de vencer — o oposto do
    // que faz sentido pra quem vende produto perecível.
    @Query("SELECT l FROM Lote l WHERE l.produto.id = :produtoId AND l.quantidade > 0 " +
            "ORDER BY CASE WHEN l.dataValidade IS NULL THEN 1 ELSE 0 END, l.dataValidade ASC, l.dataEntrada ASC")
    List<Lote> findLotesDisponiveisParaBaixa(Long produtoId);

    // ALERTA 2: Busca lotes que vão vencer antes de uma certa data e que ainda tem saldo
    @org.springframework.data.jpa.repository.Query("SELECT l FROM Lote l WHERE l.produto.empresa.id = :empresaId AND l.quantidade > 0 AND l.dataValidade <= :dataLimite")
    java.util.List<com.smartstock.backend.model.Lote> findLotesPertoDoVencimento(Long empresaId, java.time.LocalDate dataLimite);
}