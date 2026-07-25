package com.smartstock.backend.service;

import com.smartstock.backend.dto.CurvaABCDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class CurvaAbcService {

    public enum Criterio { FATURAMENTO, LUCRATIVIDADE, GIRO }

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    public List<CurvaABCDTO> calcular(Long empresaId, Criterio criterio, int dias) {
        LocalDateTime dataInicio = LocalDateTime.now().minusDays(dias);

        List<Object[]> linhas = switch (criterio) {
            case FATURAMENTO -> movimentacaoRepository.somarFaturamentoPorProdutoNoPeriodo(empresaId, dataInicio);
            case LUCRATIVIDADE -> movimentacaoRepository.somarLucroPorProdutoNoPeriodo(empresaId, dataInicio);
            case GIRO -> movimentacaoRepository.somarVolumeVendidoPorProdutoNoPeriodo(empresaId, dataInicio);
        };

        Map<Long, BigDecimal> valorPorProduto = new HashMap<>();
        for (Object[] linha : linhas) {
            Long produtoId = (Long) linha[0];
            Number valorBruto = (Number) linha[1];
            BigDecimal valor = valorBruto != null
                    ? new BigDecimal(valorBruto.toString())
                    : BigDecimal.ZERO;
            // Lucratividade pode dar negativo se o produto foi vendido abaixo do
            // custo (promoção/erro de precificação) — trava em zero pra não
            // bagunçar o acumulado percentual da curva com valores negativos.
            valorPorProduto.put(produtoId, valor.max(BigDecimal.ZERO));
        }

        List<Produto> todosProdutos = produtoRepository.findByEmpresaId(empresaId);

        // Só entra na curva o produto que existe hoje no catálogo. Produtos sem
        // registro na query de vendas (nunca venderam no período) recebem 0.
        List<Produto> produtosOrdenados = new ArrayList<>(todosProdutos);
        produtosOrdenados.sort((p1, p2) -> {
            BigDecimal v1 = valorPorProduto.getOrDefault(p1.getId(), BigDecimal.ZERO);
            BigDecimal v2 = valorPorProduto.getOrDefault(p2.getId(), BigDecimal.ZERO);
            int cmp = v2.compareTo(v1);
            return cmp != 0 ? cmp : p1.getNome().compareToIgnoreCase(p2.getNome());
        });

        BigDecimal totalGeral = valorPorProduto.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CurvaABCDTO> resultado = new ArrayList<>();

        if (totalGeral.compareTo(BigDecimal.ZERO) == 0) {
            // Empresa sem nenhuma venda no período (loja nova, ou período curto
            // demais) — não dá pra calcular percentual acumulado com divisor
            // zero. Devolve tudo como classe C ao invés de dividir por zero.
            for (Produto p : produtosOrdenados) {
                resultado.add(montarItem(p, BigDecimal.ZERO, 0.0, "C"));
            }
            return resultado;
        }

        // Mesmo algoritmo de agrupamento em blocos de valor idêntico do restante
        // do sistema (evita produtos com o mesmo faturamento caírem em classes
        // diferentes só pela ordem de desempate).
        BigDecimal acumulado = BigDecimal.ZERO;
        int i = 0;
        while (i < produtosOrdenados.size()) {
            BigDecimal valorDoBloco = valorPorProduto.getOrDefault(produtosOrdenados.get(i).getId(), BigDecimal.ZERO);
            int fimBloco = i;
            while (fimBloco < produtosOrdenados.size()
                    && valorPorProduto.getOrDefault(produtosOrdenados.get(fimBloco).getId(), BigDecimal.ZERO).compareTo(valorDoBloco) == 0) {
                fimBloco++;
            }

            int quantidadeNoBloco = fimBloco - i;
            acumulado = acumulado.add(valorDoBloco.multiply(BigDecimal.valueOf(quantidadeNoBloco)));

            double percentual = acumulado.divide(totalGeral, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            String classe = percentual <= 80.0 ? "A" : percentual <= 95.0 ? "B" : "C";

            for (int k = i; k < fimBloco; k++) {
                Produto p = produtosOrdenados.get(k);
                resultado.add(montarItem(p, valorDoBloco, percentual, classe));
            }

            i = fimBloco;
        }

        return resultado;
    }

    private CurvaABCDTO montarItem(Produto p, BigDecimal valor, double percentualAcumulado, String classe) {
        CurvaABCDTO item = new CurvaABCDTO();
        item.setProdutoId(p.getId());
        item.setNomeProduto(p.getNome());
        item.setQuantidade(p.getQuantidade() != null ? p.getQuantidade() : 0);
        item.setValorTotal(valor);
        item.setPercentualAcumulado(Math.round(percentualAcumulado * 100.0) / 100.0);
        item.setClasse(classe);
        return item;
    }
}
