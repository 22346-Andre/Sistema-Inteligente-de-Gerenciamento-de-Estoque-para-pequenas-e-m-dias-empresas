package com.smartstock.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * resposta estruturada do Webhook — antes era só uma String solta
 * ("Webhook da X recebido! 2 itens..."), o que obrigava quem integra a
 * fazer parsing de texto pra saber se algo falhou. Agora o canal externo
 * recebe, de forma explícita e parseável, quantos itens deram certo, quais
 * falharam e por quê.
 */
@Data
public class VendaExternaResultadoDTO {
    private String chaveVendaExterna;
    private int itensProcessados;
    private int itensComFalha;
    private List<String> falhas = new ArrayList<>();

    public void adicionarFalha(String codigoBarras, String motivo) {
        this.falhas.add(codigoBarras + ": " + motivo);
        this.itensComFalha++;
    }
}
