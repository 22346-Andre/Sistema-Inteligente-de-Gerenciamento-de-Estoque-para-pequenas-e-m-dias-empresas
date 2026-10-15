package com.smartstock.backend.repository;

import com.smartstock.backend.model.WebhookItemProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookItemProcessadoRepository extends JpaRepository<WebhookItemProcessado, Long> {
    boolean existsByEmpresaIdAndOrigemAndPedidoIdAndCodigoBarras(
            Long empresaId, String origem, String pedidoId, String codigoBarras);
}
