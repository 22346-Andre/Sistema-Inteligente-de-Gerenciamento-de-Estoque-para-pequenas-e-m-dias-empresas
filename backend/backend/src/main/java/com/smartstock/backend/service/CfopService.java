package com.smartstock.backend.service;

import com.smartstock.backend.model.TipoMovimentacao;
import org.springframework.stereotype.Service;

@Service
public class CfopService {

    /**
     * Calcula o CFOP correto com base nas regras da SEFAZ
     */
    public String calcularCfop(TipoMovimentacao tipo, boolean mesmoEstado, boolean temSubstituicaoTributaria) {


        if (tipo == TipoMovimentacao.QUEBRA_PERDA) {
            return "5.927"; // Baixa de stock por perecimento/avaria
        }


        if (tipo == TipoMovimentacao.ENTRADA) {
            if (mesmoEstado) {
                return temSubstituicaoTributaria ? "1.403" : "1.102";
            } else {
                return temSubstituicaoTributaria ? "2.403" : "2.102";
            }
        }


        if (tipo == TipoMovimentacao.SAIDA) {
            if (mesmoEstado) {
                // Venda para dentro do MA
                return temSubstituicaoTributaria ? "5.405" : "5.102";
            } else {
                // Venda para fora do MA
                return temSubstituicaoTributaria ? "6.404" : "6.102";
            }
        }

        return "0.000"; // Retorno padrão caso caia num tipo não mapeado
    }
}
