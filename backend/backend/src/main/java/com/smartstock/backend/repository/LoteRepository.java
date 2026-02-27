package com.smartstock.backend.repository;

import com.smartstock.backend.model.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {

    //  FEFO/FIFO
    // Pega os lotes com saldo, ordena pelo que vence primeiro.
    // Se não tiver data de validade, ordena pelo que entrou primeiro no estoque.
    @Query("SELECT l FROM Lote l WHERE l.produto.id = :produtoId AND l.quantidade > 0 " +
            "ORDER BY l.dataValidade ASC, l.dataEntrada ASC")
    List<Lote> findLotesDisponiveisParaBaixa(Long produtoId);

    // ALERTA 2: Busca lotes que vão vencer antes de uma certa data e que ainda tem saldo
    @org.springframework.data.jpa.repository.Query("SELECT l FROM Lote l WHERE l.produto.empresa.id = :empresaId AND l.quantidade > 0 AND l.dataValidade <= :dataLimite")
    java.util.List<com.smartstock.backend.model.Lote> findLotesPertoDoVencimento(Long empresaId, java.time.LocalDate dataLimite);
}