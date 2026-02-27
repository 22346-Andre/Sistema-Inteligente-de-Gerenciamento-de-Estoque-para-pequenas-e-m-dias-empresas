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

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    public EstatisticasDTO gerarEstatisticas() {
        Long empresaId = getEmpresaIdLogada();
        EstatisticasDTO dto = new EstatisticasDTO();

        // 1. CAPITAL IMOBILIZADO
        BigDecimal totalEstoque = produtoRepository.calcularValorTotalEstoque(empresaId);
        dto.setCapitalImobilizado(totalEstoque);

        // 2. GIRO DE ESTOQUE (Últimos 30 dias)
        // Fórmula simplificada: (Total Vendido / Estoque Atual)
        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);
        Integer totalSaidas = movimentacaoRepository.sumSaidasUltimosDias(empresaId, trintaDiasAtras);
        Integer estoqueAtual = produtoRepository.sumQuantidadeTotalEstoque(empresaId);

        double giro = 0.0;
        if (estoqueAtual > 0) {
            giro = (double) totalSaidas / estoqueAtual;
        }
        dto.setGiroEstoque30Dias(Math.round(giro * 100.0) / 100.0); // Arredonda a 2 casas decimais

        // 3. CURVA ABC
        List<Produto> produtosOrdenados = produtoRepository.findProdutosOrdenadosPorValorTotal(empresaId);
        List<CurvaABCDTO> curvaABC = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;

        for (Produto p : produtosOrdenados) {
            CurvaABCDTO item = new CurvaABCDTO();
            item.setNomeProduto(p.getNome());
            item.setQuantidade(p.getQuantidade());

            BigDecimal valorItem = p.getPreco().multiply(new BigDecimal(p.getQuantidade()));
            item.setValorTotal(valorItem);

            acumulado = acumulado.add(valorItem);

            // Calcula o percentual acumulado em relação ao capital total
            double percentual = 0.0;
            if (totalEstoque.compareTo(BigDecimal.ZERO) > 0) {
                percentual = acumulado.divide(totalEstoque, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            }
            item.setPercentualAcumulado(Math.round(percentual * 100.0) / 100.0);

            // Classificação ABC
            if (percentual <= 80.0) {
                item.setClasse("A"); // 80% do capital (Os mais importantes)
            } else if (percentual <= 95.0) {
                item.setClasse("B"); // Próximos 15%
            } else {
                item.setClasse("C"); // Últimos 5%
            }

            curvaABC.add(item);
        }

        dto.setCurvaABC(curvaABC);
        return dto;
    }
}