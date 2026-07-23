package com.smartstock.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;

/**
 * Aplica, em toda página do PDF, um selo discreto de "sem valor fiscal" num
 * canto (não atravessa mais a página inteira) + uma faixa fina no topo.
 * Os tamanhos são calculados a partir da largura da própria página, então o
 * mesmo código funciona tanto numa folha A4 (DANFE) quanto numa bobina
 * estreita de cupom, sem estourar nem ficar ilegível em nenhuma das duas.
 */
public class MarcaDaguaSemValorFiscal extends PdfPageEventHelper {

    private static final String CARIMBO = "SEM VALOR FISCAL";
    private static final String AVISO_FAIXA = "SEM VALOR FISCAL - APENAS DEMONSTRAÇÃO";

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte canvas = writer.getDirectContentUnder();
        Rectangle pageSize = document.getPageSize();
        float largura = pageSize.getWidth();

       
        float tamanhoCarimbo = Math.max(7f, Math.min(14f, largura / 20f));
        Font fontCarimbo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, tamanhoCarimbo, new Color(200, 0, 0));
        Phrase carimbo = new Phrase(CARIMBO, fontCarimbo);

        PdfGState gState = new PdfGState();
        gState.setFillOpacity(0.35f);
        canvas.saveState();
        canvas.setGState(gState);

        float margem = 10f;
        ColumnText.showTextAligned(
                canvas, Element.ALIGN_RIGHT, carimbo,
                pageSize.getRight() - margem,
                pageSize.getBottom() + margem,
                10 // leve inclinação, efeito "carimbo" — sem atravessar o documento
        );
        canvas.restoreState();

      
        float tamanhoFaixa = Math.max(6f, Math.min(8f, largura / 40f));
        Font fontFaixa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, tamanhoFaixa, Color.WHITE);
        PdfPTable faixa = new PdfPTable(1);
        faixa.setTotalWidth(largura - document.leftMargin() - document.rightMargin());
        PdfPCell celula = new PdfPCell(new Phrase(AVISO_FAIXA, fontFaixa));
        celula.setBackgroundColor(new Color(200, 0, 0));
        celula.setHorizontalAlignment(Element.ALIGN_CENTER);
        celula.setPadding(2f);
        celula.setBorder(Rectangle.NO_BORDER);
        faixa.addCell(celula);
        faixa.writeSelectedRows(0, -1, document.leftMargin(), pageSize.getHeight() - 6, canvas);
    }
}