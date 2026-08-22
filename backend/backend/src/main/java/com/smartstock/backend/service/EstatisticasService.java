package com.smartstock.backend.service;

import com.smartstock.backend.dto.CurvaABCDTO;
import com.smartstock.backend.dto.EstatisticasDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EstatisticasService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private CurvaAbcService curvaAbcService;

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    public EstatisticasDTO gerarEstatisticas() {
        Long empresaId = getEmpresaIdLogada();
        EstatisticasDTO dto = new EstatisticasDTO();

        // 1. CAPITAL IMOBILIZADO
        BigDecimal totalEstoque = produtoRepository.calcularValorTotalEstoque(empresaId);
        dto.setCapitalImobilizado(totalEstoque != null ? totalEstoque : BigDecimal.ZERO);

        // 2. GIRO DE ESTOQUE
        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);
        Integer totalSaidas = movimentacaoRepository.sumSaidasUltimosDias(empresaId, trintaDiasAtras);
        Integer estoqueAtual = produtoRepository.sumQuantidadeTotalEstoque(empresaId);

        totalSaidas = (totalSaidas != null) ? totalSaidas : 0;
        estoqueAtual = (estoqueAtual != null) ? estoqueAtual : 0;

        // Mesma correção do GiroEstoqueService: estoque total zerado (loja
        // toda vendida, ou em fase de reposição) com saídas reais no período
        // não é "giro zero" — é giro no limite. Sem isso, esse card do
        // Dashboard mostrava "0x" justamente no cenário de maior movimento.
        double giro;
        if (estoqueAtual > 0) {
            giro = (double) totalSaidas / estoqueAtual;
        } else if (totalSaidas > 0) {
            giro = totalSaidas;
        } else {
            giro = 0.0;
        }
        dto.setGiroEstoque(Math.round(giro * 100.0) / 100.0);

        // 3. TOTAIS PARA O DASHBOARD
        long totalProds = produtoRepository.countByEmpresaId(empresaId);
        dto.setTotalProdutos(totalProds);

        List<Produto> criticos = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresaId);
        dto.setProdutosCriticos((long) criticos.size());

        
        List<CurvaABCDTO> curvaABC = curvaAbcService.calcular(empresaId, CurvaAbcService.Criterio.FATURAMENTO, 90);
        dto.setCurvaABC(curvaABC);
        return dto;
    }
}