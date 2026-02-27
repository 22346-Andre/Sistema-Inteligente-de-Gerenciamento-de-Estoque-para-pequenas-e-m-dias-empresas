package com.smartstock.backend.repository;

import com.smartstock.backend.model.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    // Traz só os fornecedores da empresa logada
    List<Fornecedor> findByEmpresaId(Long empresaId);

    // Verifica se A EMPRESA LOGADA já cadastrou esse CNPJ
    Optional<Fornecedor> findByCnpjAndEmpresaId(String cnpj, Long empresaId);

    // Conta quantos fornecedores a empresa tem
    long countByEmpresaId(Long empresaId);
}