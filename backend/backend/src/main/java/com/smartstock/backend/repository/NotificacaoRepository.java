package com.smartstock.backend.repository;

import com.smartstock.backend.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);

    List<Notificacao> findByEmpresaIdAndLidaFalseOrderByDataCriacaoDesc(Long empresaId);

    long countByEmpresaIdAndLidaFalse(Long empresaId);
}
