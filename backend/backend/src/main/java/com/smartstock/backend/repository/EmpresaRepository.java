package com.smartstock.backend.repository;

import com.smartstock.backend.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    // Ajuda a bloquear cadastros duplicados
    boolean existsByCnpj(String cnpj);
    List<Empresa> findByUltimoAcessoBefore(LocalDateTime data);

    //  base da correção de IDOR no Webhook — a empresa é sempre
    // derivada do segredo recebido no header, nunca de um empresaId que o
    // cliente/canal externo poderia mandar livremente no corpo da requisição.
    Optional<Empresa> findByWebhookSecret(String webhookSecret);
}