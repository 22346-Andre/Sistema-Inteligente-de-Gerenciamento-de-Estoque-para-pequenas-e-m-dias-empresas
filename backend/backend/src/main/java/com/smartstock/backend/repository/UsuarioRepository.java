package com.smartstock.backend.repository;

import com.smartstock.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    //  Buscar funcionários de uma empresa específica
    List<Usuario> findByEmpresaId(Long empresaId);
}