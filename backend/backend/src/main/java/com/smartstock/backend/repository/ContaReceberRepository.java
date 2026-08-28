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

    //  Usado pelo webhook da Delfinance pra achar qual fiado foi pago
    java.util.Optional<ContaReceber> findByPixCorrelationId(String pixCorrelationId);

    // Ativo Circulante do Balanço Patrimonial: soma de tudo que ainda está
    // pendente de recebimento (PENDENTE ou ATRASADO) — dinheiro que a
    // empresa tem a receber de clientes, mas ainda não recebeu.
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(c.valor), 0) FROM ContaReceber c " +
           "WHERE c.empresa.id = :empresaId AND c.status IN ('PENDENTE', 'ATRASADO')")
    java.math.BigDecimal somarContasAReceberEmAberto(@org.springframework.data.repository.query.Param("empresaId") Long empresaId);
}
