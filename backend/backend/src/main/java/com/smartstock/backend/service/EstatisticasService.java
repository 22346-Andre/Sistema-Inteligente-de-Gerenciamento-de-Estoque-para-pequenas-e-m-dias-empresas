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
        dto.setCapitalImobilizado(totalEstoque != null ? totalEstoque : BigDecimal.ZERO);

        // 2. GIRO DE ESTOQUE
        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);
        Integer totalSaidas = movimentacaoRepository.sumSaidasUltimosDias(empresaId, trintaDiasAtras);
        Integer estoqueAtual = produtoRepository.sumQuantidadeTotalEstoque(empresaId);

        totalSaidas = (totalSaidas != null) ? totalSaidas : 0;
        estoqueAtual = (estoqueAtual != null) ? estoqueAtual : 0;

        double giro = 0.0;
        if (estoqueAtual > 0) {
            giro = (double) totalSaidas / estoqueAtual;
        }
        dto.setGiroEstoque(Math.round(giro * 100.0) / 100.0);

        // 3. TOTAIS PARA O DASHBOARD
        long totalProds = produtoRepository.countByEmpresaId(empresaId);
        dto.setTotalProdutos(totalProds);

        List<Produto> criticos = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresaId);
        dto.setProdutosCriticos((long) criticos.size());

        // 4. CURVA ABC
        // A query já traz ordenado por valor (desc) e só com quantidade > 0, mas não
        // garante um desempate determinístico entre produtos de valor IGUAL — e o
        // corte por classe (A ≤80%, B ≤95%, C resto) era aplicado item a item. Isso
        // fazia produtos de valor idêntico poderem cair em classes diferentes (até A
        // e C) só por causa de QUAL deles a soma acumulada passava primeiro.
        // Corrigido do mesmo jeito que em ProdutoService.listarTodos(): classifica
        // em blocos de valor igual, todos do bloco recebem a classe do total do bloco.
        List<Produto> produtosOrdenados = produtoRepository.findProdutosOrdenadosPorValorTotal(empresaId);
        List<CurvaABCDTO> curvaABC = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;

        int i = 0;
        while (i < produtosOrdenados.size()) {
            BigDecimal valorDoBloco = valorEmEstoque(produtosOrdenados.get(i));
            int fimBloco = i;
            while (fimBloco < produtosOrdenados.size()
                    && valorEmEstoque(produtosOrdenados.get(fimBloco)).compareTo(valorDoBloco) == 0) {
                fimBloco++;
            }

            int quantidadeNoBloco = fimBloco - i;
            acumulado = acumulado.add(valorDoBloco.multiply(BigDecimal.valueOf(quantidadeNoBloco)));

            double percentual = 0.0;
            if (totalEstoque != null && totalEstoque.compareTo(BigDecimal.ZERO) > 0) {
                percentual = acumulado.divide(totalEstoque, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            }
            String classe = percentual <= 80.0 ? "A" : percentual <= 95.0 ? "B" : "C";

            for (int k = i; k < fimBloco; k++) {
                Produto p = produtosOrdenados.get(k);
                CurvaABCDTO item = new CurvaABCDTO();
                item.setNomeProduto(p.getNome());
                item.setQuantidade(p.getQuantidade() != null ? p.getQuantidade() : 0);
                item.setValorTotal(valorEmEstoque(p));
                item.setPercentualAcumulado(Math.round(percentual * 100.0) / 100.0);
                item.setClasse(classe);
                curvaABC.add(item);
            }

            i = fimBloco;
        }

        dto.setCurvaABC(curvaABC);
        return dto;
    }

    /** Valor total do produto parado em estoque: custo unitário × quantidade. */
    private BigDecimal valorEmEstoque(Produto p) {
        BigDecimal precoCusto = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
        int qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
        return precoCusto.multiply(new BigDecimal(qtd));
    }
}