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

    // Empresas inativas há tempo suficiente pra receber o aviso de 30 dias,
    // mas que ainda não receberam nenhum (avisoInatividadeEnviadoEm nulo) —
    // ver CleanService.avisarEmpresasQuaseInativas().
    List<Empresa> findByUltimoAcessoBeforeAndAvisoInatividadeEnviadoEmIsNull(LocalDateTime dataCorteAviso);

    // Empresas que já foram avisadas há 30+ dias e continuam sem logar desde
    // então — essas sim são apagadas de verdade. Ver
    // CleanService.apagarEmpresasAvisadasHaMaisDe30Dias().
    List<Empresa> findByAvisoInatividadeEnviadoEmBeforeAndUltimoAcessoBefore(
            LocalDateTime dataCorteAviso, LocalDateTime dataCorteAcesso);

    //  base da correção de IDOR no Webhook — a empresa é sempre
    // derivada do segredo recebido no header, nunca de um empresaId que o
    // cliente/canal externo poderia mandar livremente no corpo da requisição.
    Optional<Empresa> findByWebhookSecret(String webhookSecret);
}