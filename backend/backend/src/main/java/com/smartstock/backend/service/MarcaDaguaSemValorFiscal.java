package com.smartstock.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;

/**
 * Aplica, em toda página do PDF, uma marca d'água diagonal + uma faixa de aviso
 * no topo, deixando explícito que o documento NÃO tem valor fiscal.
 * Reutilizável por qualquer gerador de nota fiscal mock (DANFE, Cupom, etc).
 */
public class MarcaDaguaSemValorFiscal extends PdfPageEventHelper {

    private static final String AVISO = "DOCUMENTO SEM VALOR FISCAL - EMITIDO APENAS PARA FINS DE DEMONSTRAÇÃO E TESTE";

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte canvas = writer.getDirectContentUnder();
        Rectangle pageSize = document.getPageSize();

        // --- Marca d'água diagonal, semi-transparente, atravessando a página ---
        Font fontMarca = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, new Color(220, 0, 0));
        Phrase marca = new Phrase(AVISO, fontMarca);

        PdfGState gState = new PdfGState();
        gState.setFillOpacity(0.20f);
        canvas.saveState();
        canvas.setGState(gState);

        ColumnText.showTextAligned(
                canvas, Element.ALIGN_CENTER, marca,
                (pageSize.getLeft() + pageSize.getRight()) / 2,
                (pageSize.getBottom() + pageSize.getTop()) / 2,
                45 // rotação em graus, atravessando a página
        );
        canvas.restoreState();

        // --- Faixa de aviso fixa e legível no topo de cada página ---
        Font fontFaixa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        PdfPTable faixa = new PdfPTable(1);
        faixa.setTotalWidth(pageSize.getWidth() - document.leftMargin() - document.rightMargin());
        PdfPCell celula = new PdfPCell(new Phrase(AVISO, fontFaixa));
        celula.setBackgroundColor(new Color(200, 0, 0));
        celula.setHorizontalAlignment(Element.ALIGN_CENTER);
        celula.setPadding(4f);
        celula.setBorder(Rectangle.NO_BORDER);
        faixa.addCell(celula);
        faixa.writeSelectedRows(0, -1, document.leftMargin(), pageSize.getHeight() - 10, canvas);
    }
}