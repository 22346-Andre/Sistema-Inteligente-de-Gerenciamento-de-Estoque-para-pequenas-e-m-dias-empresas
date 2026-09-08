package com.smartstock.backend.repository;

import com.smartstock.backend.model.NotaFiscalImportada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotaFiscalImportadaRepository extends JpaRepository<NotaFiscalImportada, Long> {

    // Usado para bloquear reimportação da mesma nota pela mesma empresa
    Optional<NotaFiscalImportada> findByChaveAcessoAndEmpresaId(String chaveAcesso, Long empresaId);

    // Histórico de notas importadas por uma empresa (útil pra tela de auditoria futura)
    List<NotaFiscalImportada> findByEmpresaIdOrderByDataImportacaoDesc(Long empresaId);
}