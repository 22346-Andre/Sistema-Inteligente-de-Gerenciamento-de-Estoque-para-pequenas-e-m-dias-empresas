package com.smartstock.backend.repository;

import com.smartstock.backend.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    // Ajuda a bloquear cadastros duplicados
    boolean existsByCnpj(String cnpj);
}