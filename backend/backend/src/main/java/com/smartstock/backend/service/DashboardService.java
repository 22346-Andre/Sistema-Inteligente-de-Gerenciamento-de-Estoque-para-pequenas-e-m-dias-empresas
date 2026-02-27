package com.smartstock.backend.service;

import com.smartstock.backend.dto.DashboardDTO;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import com.smartstock.backend.dto.GraficoDTO;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.TipoMovimentacao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    public DashboardDTO obterResumoDashboard() {
        Long empresaId = getEmpresaIdLogada();
        DashboardDTO dashboard = new DashboardDTO();

        // 1. Card: Total de Produtos
        dashboard.setTotalProdutos(produtoRepository.countByEmpresaId(empresaId));

        // 2. Card: Estoque Baixo (Reaproveita o método que já tínhamos)
        int qtdEstoqueBaixo = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresaId).size();
        dashboard.setEstoqueBaixo(qtdEstoqueBaixo);

        // 3. Card: Total de Fornecedores
        dashboard.setTotalFornecedores(fornecedorRepository.countByEmpresaId(empresaId));

        // 4. Card: Valor em Estoque (Capital Imobilizado R$)
        dashboard.setValorEmEstoque(produtoRepository.calcularValorTotalEstoque(empresaId));

        // 5. Listagem: Últimas movimentações
        dashboard.setMovimentacoesRecentes(movimentacaoRepository.findTop5ByEmpresaIdOrderByDataMovimentacaoDesc(empresaId));

        return dashboard;
    }


    public List<GraficoDTO> obterDadosGrafico() {
        Long empresaId = getEmpresaIdLogada();

        // 1. Descobre a data de 6 dias atrás (para dar 7 dias contando com hoje)
        LocalDateTime dataInicio = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0);

        // 2. Busca no banco todas as movimentações dessa semana
        List<Movimentacao> movimentacoes = movimentacaoRepository.findMovimentacoesUltimosDias(empresaId, dataInicio);

        // 3. Monta o esqueleto do gráfico com os últimos 7 dias zerados
        Map<LocalDate, GraficoDTO> mapaGrafico = new LinkedHashMap<>();
        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 6; i >= 0; i--) {
            LocalDate dia = LocalDate.now().minusDays(i);
            mapaGrafico.put(dia, new GraficoDTO(dia.format(formatador), 0, 0));
        }

        // 4. Preenche o esqueleto com os dados reais do banco
        for (Movimentacao m : movimentacoes) {
            LocalDate diaMovimentacao = m.getDataMovimentacao().toLocalDate();

            if (mapaGrafico.containsKey(diaMovimentacao)) {
                GraficoDTO dto = mapaGrafico.get(diaMovimentacao);

                if (m.getTipo() == TipoMovimentacao.ENTRADA) {
                    dto.setEntradas(dto.getEntradas() + m.getQuantidade());
                } else if (m.getTipo() == TipoMovimentacao.SAIDA) {
                    dto.setSaidas(dto.getSaidas() + m.getQuantidade());
                }
            }
        }

        // 5. Devolve a lista pronta para o Front-end
        return new ArrayList<>(mapaGrafico.values());
    }
}