package com.smartstock.backend.dto;

import com.smartstock.backend.model.Movimentacao;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardDTO {
    // Os 4 Cartões Superiores
    private long totalProdutos;
    private int estoqueBaixo;
    private long totalFornecedores;
    private BigDecimal valorEmEstoque;

    // A listagem inferior
    private List<Movimentacao> movimentacoesRecentes;
}