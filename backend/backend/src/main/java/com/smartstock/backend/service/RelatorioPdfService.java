package com.smartstock.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RelatorioPdfService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final EmpresaRepository empresaRepository;

    public RelatorioPdfService(ProdutoRepository produtoRepository, MovimentacaoRepository movimentacaoRepository, EmpresaRepository empresaRepository) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.empresaRepository = empresaRepository;
    }

    private Empresa getEmpresaLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada no banco de dados."));
    }

    public byte[] gerarBalancoGeralPdf() {
        Empresa empresa = getEmpresaLogada();
        List<Produto> produtosDaEmpresa = produtoRepository.findByEmpresaId(empresa.getId());

        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Balanco Geral de Estoque - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            Paragraph subtitulo = new Paragraph("Gerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 1.5f, 1.5f, 1.5f, 3f});

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Produto", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Custo (R$)", fontCabecalho)); // 🚨 TEXTO ATUALIZADO
            PdfPCell h3 = new PdfPCell(new Phrase("Qtd Atual", fontCabecalho));
            PdfPCell h4 = new PdfPCell(new Phrase("Minimo", fontCabecalho));
            PdfPCell h5 = new PdfPCell(new Phrase("Fornecedor / Contato", fontCabecalho));

            h1.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            h3.setBackgroundColor(Color.LIGHT_GRAY);
            h4.setBackgroundColor(Color.LIGHT_GRAY);
            h5.setBackgroundColor(Color.LIGHT_GRAY);

            table.addCell(h1); table.addCell(h2); table.addCell(h3); table.addCell(h4); table.addCell(h5);

            for (Produto p : produtosDaEmpresa) {
                table.addCell(new Phrase(p.getNome()));


                BigDecimal preco = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
                table.addCell(new Phrase(String.format("%.2f", preco)));

                Integer qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                Integer minimo = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;

                PdfPCell cellQtd = new PdfPCell(new Phrase(String.valueOf(qtd)));
                if (qtd <= minimo) {
                    cellQtd.setBackgroundColor(new Color(255, 204, 204));
                }
                table.addCell(cellQtd);
                table.addCell(new Phrase(String.valueOf(minimo)));

                String infoFornecedor = "-";
                if (p.getFornecedor() != null) {
                    infoFornecedor = p.getFornecedor().getNome();
                    if (p.getFornecedor().getTelefone() != null && !p.getFornecedor().getTelefone().isEmpty()) {
                        infoFornecedor += "\n(" + p.getFornecedor().getTelefone() + ")";
                    }
                }
                table.addCell(new Phrase(infoFornecedor));
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    public byte[] gerarRelatorioMovimentacoesPdf() {
        Empresa empresa = getEmpresaLogada();
        List<Movimentacao> movimentacoes = movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(empresa.getId());

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Relatorio de Movimentacoes - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
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
                LocalDateTime dataMov = m.getDataMovimentacao() != null ? m.getDataMovimentacao() : LocalDateTime.now();
                table.addCell(dataMov.format(formatadorData));
                table.addCell(m.getProduto() != null ? m.getProduto().getNome() : "Desconhecido");

                PdfPCell cellTipo = new PdfPCell(new Phrase(m.getTipo() != null ? m.getTipo().name() : "-"));
                if (m.getTipo() != null && m.getTipo().name().equals("ENTRADA")) {
                    cellTipo.setBackgroundColor(new Color(204, 255, 204));
                } else {
                    cellTipo.setBackgroundColor(new Color(255, 204, 204));
                }
                table.addCell(cellTipo);
                table.addCell(String.valueOf(m.getQuantidade() != null ? m.getQuantidade() : 0));
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    public byte[] gerarRelatorioInventarioFiscalPdf() {
        Empresa empresa = getEmpresaLogada();
        List<Produto> produtosDaEmpresa = produtoRepository.findByEmpresaId(empresa.getId());

        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fontCelulas = FontFactory.getFont(FontFactory.HELVETICA, 9);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.8f, 3.5f, 1.2f, 1f, 1.5f, 1.5f, 2f});

            PdfPCell cell1 = new PdfPCell(new Phrase("ESTOQUES EXISTENTES EM\n" + empresa.getNomeFantasia().toUpperCase(), fontCabecalho));
            cell1.setColspan(2);
            cell1.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell1.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell1);

            PdfPCell cell2 = new PdfPCell(new Phrase("DE ....................................................", fontCabecalho));
            cell2.setColspan(2);
            cell2.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell2.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell2);

            String ano = String.valueOf(LocalDateTime.now().getYear());
            PdfPCell cell3 = new PdfPCell(new Phrase("DE " + ano, fontCabecalho));
            cell3.setColspan(3);
            cell3.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell3.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell3);

            String[] colunas = {"CLASSIFICAÇÃO\nFISCAL (NCM)", "DESCRIÇÃO", "QUANTIDADE", "UNIDADE", "PREÇO MÉDIO\nUNITÁRIO EM R$", "VALOR TOTAL\nEM R$", "OBSERVAÇÕES"};
            for (String col : colunas) {
                PdfPCell cell = new PdfPCell(new Phrase(col, fontCabecalho));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cell);
            }

            for (Produto p : produtosDaEmpresa) {
                String ncm = (p.getNcm() != null && !p.getNcm().isEmpty()) ? p.getNcm() : "";
                PdfPCell cellNcm = new PdfPCell(new Phrase(ncm, fontCelulas));
                cellNcm.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellNcm);

                table.addCell(new Phrase(p.getNome() != null ? p.getNome() : "", fontCelulas));

                Integer qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;
                PdfPCell cellQtd = new PdfPCell(new Phrase(String.valueOf(qtd), fontCelulas));
                cellQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellQtd);

                String unidade = (p.getUnidade() != null && !p.getUnidade().isEmpty()) ? p.getUnidade() : "UN";
                PdfPCell cellUnid = new PdfPCell(new Phrase(unidade, fontCelulas));
                cellUnid.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellUnid);


                BigDecimal preco = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
                PdfPCell cellPreco = new PdfPCell(new Phrase(String.format("%.2f", preco), fontCelulas));
                cellPreco.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cellPreco);

                BigDecimal total = preco.multiply(new BigDecimal(qtd));
                PdfPCell cellTotal = new PdfPCell(new Phrase(String.format("%.2f", total), fontCelulas));
                cellTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cellTotal);

                table.addCell(new Phrase("", fontCelulas));
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }
}