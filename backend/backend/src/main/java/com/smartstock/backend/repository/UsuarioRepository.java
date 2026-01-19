package com.smartstock.backend.repository;

import com.smartstock.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Busca usuário pelo email (útil para login e validação)
    Optional<Usuario> findByEmail(String email);
}