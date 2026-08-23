package com.smartstock.backend.service;

import com.smartstock.backend.dto.GiroEstoqueDTO;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Giro de Estoque por produto — indicador de VELOCIDADE (livro Estoques e
 * Armazenagem, seção 4.5), separado da Curva ABC (que é indicador de VALOR).
 *
 * Fórmula: giro = unidades vendidas no período / estoque atual.
 * Mesma fórmula já usada de forma agregada (empresa toda) em
 * EstatisticasService, aqui aplicada produto a produto.
 */
@Service
public class GiroEstoqueService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Cacheable(cacheNames = "giroEstoque", key = "#empresaId + '_' + #dias")
    public List<GiroEstoqueDTO> calcularPorProduto(Long empresaId, int dias) {
        LocalDateTime dataInicio = LocalDateTime.now().minusDays(dias);

        List<Object[]> linhas = movimentacaoRepository.somarVolumeVendidoPorProdutoNoPeriodo(empresaId, dataInicio);

        Map<Long, Integer> vendidoPorProduto = new HashMap<>();
        for (Object[] linha : linhas) {
            Long produtoId = (Long) linha[0];
            Number quantidade = (Number) linha[1];
            vendidoPorProduto.put(produtoId, quantidade != null ? quantidade.intValue() : 0);
        }

        // Mesma exclusão de Uso Interno da Curva ABC — giro é sobre produto
        // que vende, não sobre mobiliário/equipamento da própria empresa.
        List<Produto> produtos = produtoRepository.findByEmpresaIdParaAnaliseDeGiro(empresaId);

        List<GiroEstoqueDTO> resultado = new ArrayList<>();
        for (Produto p : produtos) {
            int estoqueAtual = p.getQuantidade() != null ? p.getQuantidade() : 0;
            int vendido = vendidoPorProduto.getOrDefault(p.getId(), 0);

            // Bug real: dividir vendido/estoqueAtual e cair pra 0.0 quando
            // estoqueAtual é zero classificava como "giro baixo/nenhuma
            // movimentação" justamente o produto que girou TANTO que esgotou
            // o estoque — o pior caso possível de leitura errada, porque é
            // exatamente quando mais precisa de reposição urgente. Estoque
            // zerado com vendas no período = giro real altíssimo, não zero.
            double giro;
            if (estoqueAtual > 0) {
                giro = (double) vendido / estoqueAtual;
            } else if (vendido > 0) {
                giro = vendido;
            } else {
                giro = 0.0;
            }

            GiroEstoqueDTO item = new GiroEstoqueDTO();
            item.setProdutoId(p.getId());
            item.setNomeProduto(p.getNome());
            item.setEstoqueAtual(estoqueAtual);
            item.setUnidadesVendidasNoPeriodo(vendido);
            item.setGiro(Math.round(giro * 100.0) / 100.0);
            // Faixas simples só pra guiar a leitura do relatório na tela —
            // não é uma classificação estatística como a curva ABC, apenas
            // um agrupamento de apoio visual.
            item.setClassificacao(giro >= 1.0 ? "ALTO" : giro >= 0.3 ? "MEDIO" : "BAIXO");

            resultado.add(item);
        }

        resultado.sort((a, b) -> Double.compare(b.getGiro(), a.getGiro()));
        return resultado;
    }
}
