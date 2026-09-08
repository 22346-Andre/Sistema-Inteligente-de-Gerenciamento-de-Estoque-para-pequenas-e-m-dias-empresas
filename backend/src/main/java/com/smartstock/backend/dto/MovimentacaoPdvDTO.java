package com.smartstock.backend.dto;

public class MovimentacaoPdvDTO {
    private String codigoBarras;
    private String tipo; // "ENTRADA" ou "SAIDA"
    private Integer quantidade;

    // Getters e Setters
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
}
