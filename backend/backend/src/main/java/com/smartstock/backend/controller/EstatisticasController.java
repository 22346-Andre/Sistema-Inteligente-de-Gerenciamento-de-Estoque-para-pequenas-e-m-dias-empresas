package com.smartstock.backend.controller;

import com.smartstock.backend.dto.CurvaABCDTO;
import com.smartstock.backend.dto.EstatisticasDTO;
import com.smartstock.backend.dto.GiroEstoqueDTO;
import com.smartstock.backend.service.CurvaAbcService;
import com.smartstock.backend.service.EstatisticasService;
import com.smartstock.backend.service.GiroEstoqueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/estatisticas")
public class EstatisticasController {

    @Autowired
    private EstatisticasService service;

    @Autowired
    private CurvaAbcService curvaAbcService;

    @Autowired
    private GiroEstoqueService giroEstoqueService;

    //  Apenas gestores podem aceder a dados financeiros profundos
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public EstatisticasDTO obterEstatisticas() {
        return service.gerarEstatisticas();
    }

    
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/curva-abc")
    public List<CurvaABCDTO> obterCurvaAbc(
            @RequestParam(defaultValue = "faturamento") String criterio,
            // "dias" é ignorado quando criterio=capital-imobilizado (esse
            // critério é sempre uma foto do estoque atual, não um período de
            // vendas) — mantido como parâmetro só pra não quebrar contrato
            // com faturamento/lucratividade.
            @RequestParam(defaultValue = "90") int dias) {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        CurvaAbcService.Criterio criterioEnum = switch (criterio.toLowerCase()) {
            case "lucratividade" -> CurvaAbcService.Criterio.LUCRATIVIDADE;
            case "capital-imobilizado" -> CurvaAbcService.Criterio.CAPITAL_IMOBILIZADO;
            default -> CurvaAbcService.Criterio.FATURAMENTO;
        };

        // Trava simples de sanidade — evita períodos absurdos (0 ou negativo
        // travaria a query; período gigante só deixa a consulta mais lenta à toa).
        int diasSeguro = Math.max(1, Math.min(dias, 730));

        return curvaAbcService.calcular(empresaId, criterioEnum, diasSeguro);
    }

    // Giro de Estoque é um relatório à parte da Curva ABC — mede VELOCIDADE
    // (saídas / estoque atual), não VALOR, então não faz sentido como um
    // "critério" da curva ABC. Ver GiroEstoqueService para a justificativa completa.
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/giro-estoque")
    public List<GiroEstoqueDTO> obterGiroEstoque(
            @RequestParam(defaultValue = "90") int dias) {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        int diasSeguro = Math.max(1, Math.min(dias, 730));

        return giroEstoqueService.calcularPorProduto(empresaId, diasSeguro);
    }
}
