package com.smartstock.backend.repository;

import com.smartstock.backend.model.Movimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    // Traz o histórico do mais novo para o mais velho
    List<Movimentacao> findAllByOrderByDataMovimentacaoDesc();
}