package com.smartstock.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;


public class MarcaDaguaSemValorFiscal extends PdfPageEventHelper {

    private static final String AVISO_CURTO = "SEM VALOR FISCAL - DEMONSTRAÇÃO";
    private static final String AVISO_LONGO = "DOCUMENTO SEM VALOR FISCAL - EMITIDO APENAS PARA FINS DE DEMONSTRAÇÃO E TESTE";

    // Abaixo dessa largura de página consideramos "bobina/cupom" e usamos o
    // texto curto + fonte ainda menor para caber num canto sem quebrar feio.
    private static final float LARGURA_LIMITE_BOBINA = 300f;

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte canvas = writer.getDirectContentUnder();
        Rectangle pageSize = document.getPageSize();
        float largura = pageSize.getWidth();

        boolean isBobina = largura < LARGURA_LIMITE_BOBINA;
        String texto = isBobina ? AVISO_CURTO : AVISO_LONGO;
        float tamanhoFonte = isBobina ? 5.5f : 6.5f;

        Font fonte = FontFactory.getFont(FontFactory.HELVETICA, tamanhoFonte, new Color(150, 150, 150));
        Phrase marca = new Phrase(texto, fonte);

        PdfGState gState = new PdfGState();
        gState.setFillOpacity(0.55f);
        canvas.saveState();
        canvas.setGState(gState);

        
        float margemDireita = document.rightMargin();
        float x = pageSize.getRight() - margemDireita;
        float y = pageSize.getBottom() + 6f;

        ColumnText.showTextAligned(
                canvas, Element.ALIGN_RIGHT, marca,
                x, y,
                0 
        );
        canvas.restoreState();
    }
}