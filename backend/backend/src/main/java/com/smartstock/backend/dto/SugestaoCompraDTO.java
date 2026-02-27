package com.smartstock.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class SugestaoCompraDTO {
    private String nomeFornecedor;
    private String telefone;
    private List<String> nomesProdutos;
    private String linkWhatsapp;
}