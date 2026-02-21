package com.smartstock.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RelatorioPdfService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    public RelatorioPdfService(ProdutoRepository produtoRepository, MovimentacaoRepository movimentacaoRepository) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    // ==========================================
    // MÉTODO 1: BALANÇO GERAL DE ESTOQUE
    // ==========================================
    public byte[] gerarBalancoGeralPdf() {
        List<Produto> todosProdutos = produtoRepository.findAll();
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Balanco Geral de Estoque - SmartStock", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            Paragraph subtitulo = new Paragraph("Gerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1.5f, 1.5f, 1.5f});

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Produto", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Preco (R$)", fontCabecalho));
            PdfPCell h3 = new PdfPCell(new Phrase("Qtd Atual", fontCabecalho));
            PdfPCell h4 = new PdfPCell(new Phrase("Minimo", fontCabecalho));

            h1.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            h3.setBackgroundColor(Color.LIGHT_GRAY);
            h4.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(h1); table.addCell(h2); table.addCell(h3); table.addCell(h4);

            for (Produto p : todosProdutos) {
                table.addCell(p.getNome());
                table.addCell(p.getPreco() != null ? p.getPreco().toString() : "0.00");

                PdfPCell cellQtd = new PdfPCell(new Phrase(String.valueOf(p.getQuantidade())));
                if (p.getQuantidade() <= p.getEstoqueMinimo()) {
                    cellQtd.setBackgroundColor(new Color(255, 204, 204));
                }
                table.addCell(cellQtd);
                table.addCell(String.valueOf(p.getEstoqueMinimo()));
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    // ==========================================
    // MÉTODO 2: HISTÓRICO DE MOVIMENTAÇÕES
    // ==========================================
    public byte[] gerarRelatorioMovimentacoesPdf() {
        List<Movimentacao> movimentacoes = movimentacaoRepository.findAllByOrderByDataMovimentacaoDesc();
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Relatorio de Movimentacoes (Entradas e Saidas)", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            Paragraph subtitulo = new Paragraph("Gerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 3f, 1.5f, 1.5f});

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Data/Hora", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Produto", fontCabecalho));
            PdfPCell h3 = new PdfPCell(new Phrase("Tipo", fontCabecalho));
            PdfPCell h4 = new PdfPCell(new Phrase("Quantidade", fontCabecalho));

            h1.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            h3.setBackgroundColor(Color.LIGHT_GRAY);
            h4.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(h1); table.addCell(h2); table.addCell(h3); table.addCell(h4);

            DateTimeFormatter formatadorData = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (Movimentacao m : movimentacoes) {
                table.addCell(m.getDataMovimentacao().format(formatadorData));
                table.addCell(m.getProduto().getNome());

                PdfPCell cellTipo = new PdfPCell(new Phrase(m.getTipo().name()));
                if (m.getTipo().name().equals("ENTRADA")) {
                    cellTipo.setBackgroundColor(new Color(204, 255, 204));
                } else {
                    cellTipo.setBackgroundColor(new Color(255, 204, 204));
                }
                table.addCell(cellTipo);
                table.addCell(String.valueOf(m.getQuantidade()));
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }
}