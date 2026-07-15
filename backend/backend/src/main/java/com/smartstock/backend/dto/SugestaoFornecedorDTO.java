package com.smartstock.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SugestaoFornecedorDTO {
    private String nomeFornecedor;
    private String telefoneFornecedor;
    private String textoMensagem;   
    private String linkWhatsApp;    
    private List<SugestaoCompraDTO> itens;
}