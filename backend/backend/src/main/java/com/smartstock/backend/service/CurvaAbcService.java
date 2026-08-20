package com.smartstock.backend.service;

import com.smartstock.backend.dto.CurvaABCDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
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

    // Curva ABC é, por definição, uma classificação por VALOR (Pareto sobre
    // valor de consumo/demanda) — Faturamento e Lucratividade multiplicam
    // quantidade por preço, então cabem aqui. Giro de estoque é uma razão
    // (saídas / estoque atual), não um valor monetário, e por isso foi
    // separado para GiroEstoqueService, evitando misturar dois indicadores
    // de gestão conceitualmente diferentes num só relatório.
    //
    // CAPITAL_IMOBILIZADO é a "Curva ABC de Estoque": também é valor (não é
    // o erro de usar volume puro que o GIRO antigo cometia), mas ao invés de
    // olhar pra vendas de um período, olha pro estoque parado HOJE
    // (quantidade em mãos × custo unitário). Responde "onde está meu capital
    // parado", não "o que mais vende" — por isso o período (dias) não se
    // aplica a esse critério, é sempre uma foto do estoque atual.
    public enum Criterio { FATURAMENTO, LUCRATIVIDADE, CAPITAL_IMOBILIZADO }

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    // Cálculo em memória (ordenação + acumulado percentual sobre todo o
    // catálogo) — é o relatório mais pesado de CPU do sistema hoje, então
    // é o que mais se beneficia de cache.
    @Cacheable(cacheNames = "curvaAbc", key = "#empresaId + '_' + #criterio + '_' + #dias")
    public List<CurvaABCDTO> calcular(Long empresaId, Criterio criterio, int dias) {
        List<Produto> todosProdutos = produtoRepository.findByEmpresaId(empresaId);

        Map<Long, BigDecimal> valorPorProduto = new HashMap<>();

        if (criterio == Criterio.CAPITAL_IMOBILIZADO) {
            // Não usa período nem movimentação: é o valor do estoque parado
            // agora — quantidade em mãos × custo unitário de cada produto.
            for (Produto p : todosProdutos) {
                BigDecimal quantidade = BigDecimal.valueOf(p.getQuantidade() != null ? p.getQuantidade() : 0);
                BigDecimal custoUnitario = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
                valorPorProduto.put(p.getId(), quantidade.multiply(custoUnitario).max(BigDecimal.ZERO));
            }
        } else {
            LocalDateTime dataInicio = LocalDateTime.now().minusDays(dias);

            List<Object[]> linhas = switch (criterio) {
                case FATURAMENTO -> movimentacaoRepository.somarFaturamentoPorProdutoNoPeriodo(empresaId, dataInicio);
                case LUCRATIVIDADE -> movimentacaoRepository.somarLucroPorProdutoNoPeriodo(empresaId, dataInicio);
                case CAPITAL_IMOBILIZADO -> throw new IllegalStateException("tratado acima"); // inatingível
            };

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
        }

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
        int totalItens = produtosOrdenados.size();

        if (totalGeral.compareTo(BigDecimal.ZERO) == 0) {
            // CAPITAL_IMOBILIZADO com tudo zero = catálogo sem custo cadastrado
            // ou sem estoque; FATURAMENTO/LUCRATIVIDADE com tudo zero = sem
            // vendas no período. Nos dois casos não dá pra dividir por zero —
            // devolve tudo como classe C.
            for (int idx = 0; idx < totalItens; idx++) {
                double percentualItens = (idx + 1) * 100.0 / totalItens;
                resultado.add(montarItem(produtosOrdenados.get(idx), BigDecimal.ZERO, 0.0, percentualItens, "C"));
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
            // Eixo X do gráfico de Pareto (Tabela Mestra do artigo): % da
            // QUANTIDADE de itens acumulada até o fim deste bloco — não
            // confundir com percentual de valor (eixo Y) calculado acima.
            double percentualItens = fimBloco * 100.0 / totalItens;
            String classe = percentual <= 80.0 ? "A" : percentual <= 95.0 ? "B" : "C";

            for (int k = i; k < fimBloco; k++) {
                Produto p = produtosOrdenados.get(k);
                resultado.add(montarItem(p, valorDoBloco, percentual, percentualItens, classe));
            }

            i = fimBloco;
        }

        return resultado;
    }

    private CurvaABCDTO montarItem(Produto p, BigDecimal valor, double percentualAcumulado, double percentualItensAcumulado, String classe) {
        CurvaABCDTO item = new CurvaABCDTO();
        item.setProdutoId(p.getId());
        item.setNomeProduto(p.getNome());
        item.setQuantidade(p.getQuantidade() != null ? p.getQuantidade() : 0);
        item.setValorTotal(valor);
        item.setPercentualAcumulado(Math.round(percentualAcumulado * 100.0) / 100.0);
        item.setPercentualItensAcumulado(Math.round(percentualItensAcumulado * 100.0) / 100.0);
        item.setClasse(classe);
        return item;
    }
}
