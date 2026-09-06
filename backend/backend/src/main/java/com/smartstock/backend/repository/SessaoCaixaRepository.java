package com.smartstock.backend.repository;

import com.smartstock.backend.model.SessaoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessaoCaixaRepository extends JpaRepository<SessaoCaixa, Long> {

    // A sessão aberta (se existir) daquele usuário específico — é o que
    // decide se ele pode abrir uma nova ou já tem uma pendente de fechar.
    Optional<SessaoCaixa> findByEmpresaIdAndUsuarioAberturaIdAndDataFechamentoIsNull(Long empresaId, Long usuarioId);

    // Histórico completo da empresa (todos os operadores), mais recente primeiro.
    List<SessaoCaixa> findByEmpresaIdOrderByDataAberturaDesc(Long empresaId);
}
