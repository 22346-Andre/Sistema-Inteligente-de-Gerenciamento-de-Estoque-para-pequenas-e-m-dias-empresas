package com.smartstock.backend.repository;

import com.smartstock.backend.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.time.LocalDateTime;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    // Ajuda a bloquear cadastros duplicados
    boolean existsByCnpj(String cnpj);
    List<Empresa> findByUltimoAcessoBefore(LocalDateTime data);
}