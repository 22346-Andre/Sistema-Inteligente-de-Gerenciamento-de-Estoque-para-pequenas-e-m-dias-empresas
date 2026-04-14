package com.smartstock.backend.repository;

import com.smartstock.backend.model.ContaReceber;
import com.smartstock.backend.model.StatusConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {
    List<ContaReceber> findByEmpresaIdOrderByDataVencimentoAsc(Long empresaId);

    List<ContaReceber> findByEmpresaIdAndStatus(Long empresaId, StatusConta status);

    //  Busca quem está PENDENTE ou ATRASADO e cuja DATA DE COBRANÇA chegou
    List<ContaReceber> findByEmpresaIdAndStatusInAndDataProximaCobrancaLessThanEqual(
            Long empresaId,
            List<StatusConta> statusList,
            LocalDate data
    );

    // Para o Scheduler de atualizar Status
    List<ContaReceber> findByStatusAndDataVencimentoLessThanEqual(StatusConta status, LocalDate data);
}
