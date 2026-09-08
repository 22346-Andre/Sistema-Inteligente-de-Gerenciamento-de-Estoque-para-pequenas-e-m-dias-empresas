package com.smartstock.backend.dto;

import com.smartstock.backend.model.Movimentacao;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardDTO {

    private long totalProdutos;
    private int estoqueBaixo;
    private long totalFornecedores;
    private BigDecimal valorEmEstoque;


    private List<Movimentacao> movimentacoesRecentes;
}