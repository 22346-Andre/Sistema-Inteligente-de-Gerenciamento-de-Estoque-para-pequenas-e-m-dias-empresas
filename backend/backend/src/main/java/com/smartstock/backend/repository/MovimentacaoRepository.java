package com.smartstock.backend.repository;

import com.smartstock.backend.model.Movimentacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    // Traz de todas as empresas)
    List<Movimentacao> findAllByOrderByDataMovimentacaoDesc();

    //  Busca SÓ as movimentações da empresa logada, ordenadas por data
    List<Movimentacao> findByEmpresaIdOrderByDataMovimentacaoDesc(Long empresaId);
}