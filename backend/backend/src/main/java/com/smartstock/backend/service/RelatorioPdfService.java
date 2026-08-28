package com.smartstock.backend.service;

import com.smartstock.backend.exception.RecursoNaoEncontradoException;

import com.lowagie.text.*;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.DespesaRepository;
import com.smartstock.backend.repository.ContaReceberRepository;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.MovimentoCaixaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import java.util.HashMap;
import java.util.Map;

@Service
public class RelatorioPdfService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final DespesaRepository despesaRepository;
    private final MovimentoCaixaRepository movimentoCaixaRepository;
    private final ContaReceberRepository contaReceberRepository;

    public RelatorioPdfService(ProdutoRepository produtoRepository, MovimentacaoRepository movimentacaoRepository,
                                EmpresaRepository empresaRepository, DespesaRepository despesaRepository,
                                MovimentoCaixaRepository movimentoCaixaRepository, ContaReceberRepository contaReceberRepository) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.empresaRepository = empresaRepository;
        this.despesaRepository = despesaRepository;
        this.movimentoCaixaRepository = movimentoCaixaRepository;
        this.contaReceberRepository = contaReceberRepository;
    }

    private Empresa getEmpresaLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada no banco de dados."));
    }
    private LocalDateTime[] validarEParsearDatas(String dataInicio, String dataFim) {
        if (dataInicio == null || dataInicio.trim().isEmpty() || dataFim == null || dataFim.trim().isEmpty()) {
            return null; 
        }
        try {
            LocalDateTime inicio = LocalDateTime.parse(dataInicio + "T00:00:00");
            LocalDateTime fim = LocalDateTime.parse(dataFim + "T23:59:59");
            if (inicio.isAfter(fim)) {
                throw new IllegalArgumentException("A data final deve ser maior ou igual à data inicial.");
            }
            return new LocalDateTime[]{inicio, fim};
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida. Verifique se o dia existe no mês e ano selecionados.");
        }
    }

  public byte[] gerarBalancoGeralPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        List<Produto> produtosDaEmpresa = produtoRepository.findByEmpresaId(empresa.getId());
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);

        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Balanço Geral de Estoque - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String textoPeriodo = (limites != null) 
                    ? "Posição Retroativa até: " + limites[1].format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n"
                    : "";
            Paragraph subtitulo = new Paragraph(textoPeriodo + "Gerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 1.5f, 1.5f, 1.5f, 3f});

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Produto", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Custo (R$)", fontCabecalho));
            PdfPCell h3 = new PdfPCell(new Phrase("Qtd Apurada", fontCabecalho));
            PdfPCell h4 = new PdfPCell(new Phrase("Mínimo", fontCabecalho));
            PdfPCell h5 = new PdfPCell(new Phrase("Fornecedor / Contato", fontCabecalho));

            h1.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            h3.setBackgroundColor(Color.LIGHT_GRAY);
            h4.setBackgroundColor(Color.LIGHT_GRAY);
            h5.setBackgroundColor(Color.LIGHT_GRAY);

            table.addCell(h1); table.addCell(h2); table.addCell(h3); table.addCell(h4); table.addCell(h5);

            for (Produto p : produtosDaEmpresa) {
                Integer qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;

                // Descobre a quantidade exata no passado
                if (limites != null) {
                    LocalDateTime dataCorte = limites[1];

                    // Se o produto foi cadastrado DEPOIS da data de corte, ele ainda não
                    // existia no sistema naquela data — não faz sentido tentar reconstruir
                    // uma quantidade retroativa para ele. Pula direto.
                    if (p.getDataCriacao() != null && p.getDataCriacao().isAfter(dataCorte)) {
                        continue;
                    }

                    List<Movimentacao> movsFuturas = movimentacaoRepository.findByProdutoIdAndDataMovimentacaoBetween(p.getId(), dataCorte.plusSeconds(1), LocalDateTime.now());
                    
                    for (Movimentacao m : movsFuturas) {
                        if (m.getTipo() == TipoMovimentacao.ENTRADA) {
                            qtd -= m.getQuantidade();
                        } else {
                            qtd += m.getQuantidade();
                        }
                    }
                    if (qtd <= 0) continue; // Pula se no passado esse produto não estava no estoque
                }

                table.addCell(new Phrase(p.getNome()));

                BigDecimal preco = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
                table.addCell(new Phrase(String.format("%.2f", preco)));

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

    public byte[] gerarRelatorioMovimentacoesPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        List<Movimentacao> movimentacoes = movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(empresa.getId());
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);
        if (limites != null) {
            movimentacoes = movimentacoes.stream()
                .filter(m -> m.getDataMovimentacao() != null && !m.getDataMovimentacao().isBefore(limites[0]) && !m.getDataMovimentacao().isAfter(limites[1]))
                .collect(Collectors.toList());
        }
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
                if (m.getTipo() == com.smartstock.backend.model.TipoMovimentacao.ENTRADA) {
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

 public byte[] gerarRelatorioInventarioFiscalPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        List<Produto> produtosDaEmpresa = produtoRepository.findByEmpresaId(empresa.getId());
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);

        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Inventário Fiscal de Estoque - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String textoPeriodo = (limites != null) 
                    ? "Posição Retroativa até: " + limites[1].format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n"
                    : "";
            Paragraph subtitulo = new Paragraph(textoPeriodo + "Gerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
            Font fontCelulas = FontFactory.getFont(FontFactory.HELVETICA, 7);

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.2f, 2.8f, 1f, 2.5f, 0.8f, 0.8f, 1.2f, 1.2f, 1.5f});

            PdfPCell cell1 = new PdfPCell(new Phrase("ESTOQUES EXISTENTES EM\n" + empresa.getNomeFantasia().toUpperCase(), fontCabecalho));
            cell1.setColspan(3);
            cell1.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell1.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell1);

            String periodoTabela = (limites != null) 
                ? "POSIÇÃO APURADA ATÉ " + limites[1].format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "POSIÇÃO ATUAL ATÉ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            PdfPCell cell2 = new PdfPCell(new Phrase(periodoTabela, fontCabecalho));
            cell2.setColspan(3);
            cell2.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell2.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell2);

            String ano = String.valueOf(LocalDateTime.now().getYear());
            PdfPCell cell3 = new PdfPCell(new Phrase("EXERCÍCIO DE " + ano, fontCabecalho));
            cell3.setColspan(3);
            cell3.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell3.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell3);

            String[] colunas = {"NCM", "DESCRIÇÃO", "CFOP", "IMPOSTOS ASSOCIADOS", "QTD", "UNID.", "PREÇO MÉDIO\nUNIT. (R$)", "VALOR TOTAL\n(R$)", "OBSERVAÇÕES"};
            for (String col : colunas) {
                PdfPCell cell = new PdfPCell(new Phrase(col, fontCabecalho));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                table.addCell(cell);
            }

            for (Produto p : produtosDaEmpresa) {
                Integer qtd = p.getQuantidade() != null ? p.getQuantidade() : 0;

                // CÁLCULO RETROATIVO
                if (limites != null) {
                    LocalDateTime dataCorte = limites[1];

                    // Se o produto foi cadastrado DEPOIS da data de corte, ele ainda não
                    // existia no sistema naquela data — não faz sentido tentar reconstruir
                    // uma quantidade retroativa para ele. Pula direto.
                    if (p.getDataCriacao() != null && p.getDataCriacao().isAfter(dataCorte)) {
                        continue;
                    }

                    List<Movimentacao> movsFuturas = movimentacaoRepository.findByProdutoIdAndDataMovimentacaoBetween(p.getId(), dataCorte.plusSeconds(1), LocalDateTime.now());
                    
                    for (Movimentacao m : movsFuturas) {
                        if (m.getTipo() == TipoMovimentacao.ENTRADA) {
                            qtd -= m.getQuantidade();
                        } else {
                            qtd += m.getQuantidade();
                        }
                    }
                    if (qtd <= 0) continue; 
                }

                String ncm = (p.getNcm() != null && !p.getNcm().isEmpty()) ? p.getNcm() : "-";
                PdfPCell cellNcm = new PdfPCell(new Phrase(ncm, fontCelulas));
                cellNcm.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellNcm);

                table.addCell(new Phrase(p.getNome() != null ? p.getNome() : "", fontCelulas));

                String cfop = (p.getCfop() != null && !p.getCfop().isEmpty()) ? p.getCfop() : "-";
                PdfPCell cellCfop = new PdfPCell(new Phrase(cfop, fontCelulas));
                cellCfop.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellCfop);

                StringBuilder impostosStr = new StringBuilder();
                if (p.getImpostos() != null && !p.getImpostos().isEmpty()) {
                    for (var imposto : p.getImpostos()) {
                        impostosStr.append(imposto.getSigla())
                                .append(": ")
                                .append(imposto.getAliquota()).append("%\n");
                    }
                } else {
                    impostosStr.append("Isento / Não inf.");
                }
                PdfPCell cellImpostos = new PdfPCell(new Phrase(impostosStr.toString().trim(), fontCelulas));
                table.addCell(cellImpostos);

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

    /**
     * Relatório Contábil (DRE simplificado): Receita Bruta − CMV = Lucro
     * Bruto, menos Despesas Operacionais (pagas no período) e Perdas por
     * quebra/avaria. Ainda é "simplificado" — não inclui tributos sobre o
     * lucro nem outras contas do plano de contas —, mas com o módulo de
     * Despesas agora fecha um resultado operacional de verdade, não mais
     * uma aproximação que ignorava custo fixo do negócio.
     */
    public byte[] gerarRelatorioContabilPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);
        // Sem filtro de data = todo o histórico. As agregações usam BETWEEN,
        // então precisam de limites concretos mesmo quando o usuário não
        // escolheu nenhum — 01/01/2000 cobre qualquer dado real do sistema.
        LocalDateTime dataInicioEfetiva = limites != null ? limites[0] : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime dataFimEfetiva = limites != null ? limites[1] : LocalDateTime.now();

        BigDecimal receitaBruta = movimentacaoRepository.somarFaturamentoNoIntervalo(empresa.getId(), dataInicioEfetiva, dataFimEfetiva);
        BigDecimal cmv = movimentacaoRepository.somarCmvNoIntervalo(empresa.getId(), dataInicioEfetiva, dataFimEfetiva);
        BigDecimal perdas = movimentacaoRepository.somarPerdasNoIntervalo(empresa.getId(), dataInicioEfetiva, dataFimEfetiva);
        BigDecimal despesasPagas = despesaRepository.somarDespesasPagasNoIntervalo(
                empresa.getId(), dataInicioEfetiva.toLocalDate(), dataFimEfetiva.toLocalDate());
        BigDecimal lucroBruto = receitaBruta.subtract(cmv);
        BigDecimal resultadoOperacional = lucroBruto.subtract(despesasPagas).subtract(perdas);
        BigDecimal margemBrutaPct = receitaBruta.compareTo(BigDecimal.ZERO) > 0
                ? lucroBruto.divide(receitaBruta, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Relatório Contábil (DRE Simplificado) - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String textoPeriodo = (limites != null)
                    ? dataInicioEfetiva.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a " + dataFimEfetiva.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "Todo o histórico";
            Paragraph subtitulo = new Paragraph("Período: " + textoPeriodo + "\nGerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            Font fontLinha = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fontLinhaNegrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font fontResultado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1.4f});
            table.setSpacingBefore(10);

            adicionarLinhaDre(table, "Receita Bruta de Vendas", receitaBruta, fontLinha, false);
            adicionarLinhaDre(table, "(-) CMV (Custo das Mercadorias Vendidas)", cmv.negate(), fontLinha, false);
            adicionarLinhaDre(table, "(=) Lucro Bruto", lucroBruto, fontLinhaNegrito, true);
            adicionarLinhaDre(table, "(-) Despesas Operacionais pagas no período", despesasPagas.negate(), fontLinha, false);
            adicionarLinhaDre(table, "(-) Perdas por Quebra/Avaria no período", perdas.negate(), fontLinha, false);
            adicionarLinhaDre(table, "(=) Resultado Operacional do Período", resultadoOperacional, fontResultado, true);

            document.add(table);

            Paragraph margem = new Paragraph("\nMargem Bruta: " + String.format("%.2f", margemBrutaPct) + "%", fontLinhaNegrito);
            document.add(margem);

            // Contas a Pagar em aberto não entra no resultado do período (é
            // Passivo, não despesa incorrida) — mostrado à parte, como
            // informação complementar de saúde financeira.
            BigDecimal contasAPagarEmAberto = despesaRepository.somarContasAPagarEmAberto(empresa.getId());
            Paragraph passivo = new Paragraph(
                    "Contas a Pagar em aberto (não incluídas no resultado acima): R$ " + String.format("%.2f", contasAPagarEmAberto),
                    fontLinha);
            document.add(passivo);

            Font fontAviso = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);
            Paragraph aviso = new Paragraph(
                    "\n\nEste é um relatório gerencial simplificado, não um Demonstrativo de Resultado do Exercício (DRE) " +
                    "oficial para fins contábeis/fiscais. Ele não inclui tributos sobre o lucro nem outras contas do " +
                    "plano de contas da empresa — o sistema não rastreia essas " +
                    "informações. Para o DRE oficial e demais demonstrações contábeis, consulte o contador responsável.",
                    fontAviso);
            document.add(aviso);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private void adicionarLinhaDre(PdfPTable table, String rotulo, BigDecimal valor, Font font, boolean linhaDeStaque) {
        PdfPCell celulaRotulo = new PdfPCell(new Phrase(rotulo, font));
        celulaRotulo.setBorder(linhaDeStaque ? Rectangle.TOP : Rectangle.NO_BORDER);
        celulaRotulo.setPaddingTop(4);
        celulaRotulo.setPaddingBottom(4);
        table.addCell(celulaRotulo);

        String valorFormatado = "R$ " + String.format("%.2f", valor);
        PdfPCell celulaValor = new PdfPCell(new Phrase(valorFormatado, font));
        celulaValor.setBorder(linhaDeStaque ? Rectangle.TOP : Rectangle.NO_BORDER);
        celulaValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celulaValor.setPaddingTop(4);
        celulaValor.setPaddingBottom(4);
        table.addCell(celulaValor);
    }

    /**
     * DFC simplificada: saldo inicial, entradas e saídas do período
     * agrupadas por origem, e saldo final. Diferente do modelo contábil
     * clássico (que separa Operacional/Investimento/Financiamento em
     * blocos formais), aqui cada origem já indica a natureza — Venda PDV e
     * Recebimento de Fiado são essencialmente operacionais, Aporte/Retirada
     * de Sócio são financiamento.
     */
    public byte[] gerarRelatorioFluxoCaixaPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);
        LocalDateTime dataInicioEfetiva = limites != null ? limites[0] : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime dataFimEfetiva = limites != null ? limites[1] : LocalDateTime.now();

        BigDecimal saldoInicial = movimentoCaixaRepository.calcularSaldoAntesDe(empresa.getId(), dataInicioEfetiva);
        List<Object[]> linhas = movimentoCaixaRepository.somarPorTipoEOrigemNoIntervalo(empresa.getId(), dataInicioEfetiva, dataFimEfetiva);

        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSaidas = BigDecimal.ZERO;
        java.util.Map<String, BigDecimal> entradasPorOrigem = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> saidasPorOrigem = new java.util.LinkedHashMap<>();

        for (Object[] linha : linhas) {
            String tipo = linha[0].toString();
            String origem = formatarOrigemCaixa(linha[1].toString());
            BigDecimal valor = (BigDecimal) linha[2];

            if ("ENTRADA".equals(tipo)) {
                totalEntradas = totalEntradas.add(valor);
                entradasPorOrigem.merge(origem, valor, BigDecimal::add);
            } else {
                totalSaidas = totalSaidas.add(valor);
                saidasPorOrigem.merge(origem, valor, BigDecimal::add);
            }
        }

        BigDecimal saldoFinal = saldoInicial.add(totalEntradas).subtract(totalSaidas);

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Demonstração de Fluxo de Caixa (DFC Simplificada) - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String textoPeriodo = (limites != null)
                    ? dataInicioEfetiva.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a " + dataFimEfetiva.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "Todo o histórico";
            Paragraph subtitulo = new Paragraph("Período: " + textoPeriodo + "\nGerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            Font fontLinha = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fontLinhaNegrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font fontResultado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font fontSecao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1.4f});
            table.setSpacingBefore(10);

            adicionarLinhaDre(table, "Saldo Inicial do Período", saldoInicial, fontLinhaNegrito, false);

            PdfPCell secaoEntradas = new PdfPCell(new Phrase("Entradas", fontSecao));
            secaoEntradas.setColspan(2);
            secaoEntradas.setBorder(Rectangle.NO_BORDER);
            secaoEntradas.setPaddingTop(10);
            table.addCell(secaoEntradas);
            for (var entry : entradasPorOrigem.entrySet()) {
                adicionarLinhaDre(table, "   " + entry.getKey(), entry.getValue(), fontLinha, false);
            }
            adicionarLinhaDre(table, "(=) Total de Entradas", totalEntradas, fontLinhaNegrito, true);

            PdfPCell secaoSaidas = new PdfPCell(new Phrase("Saídas", fontSecao));
            secaoSaidas.setColspan(2);
            secaoSaidas.setBorder(Rectangle.NO_BORDER);
            secaoSaidas.setPaddingTop(10);
            table.addCell(secaoSaidas);
            for (var entry : saidasPorOrigem.entrySet()) {
                adicionarLinhaDre(table, "   " + entry.getKey(), entry.getValue().negate(), fontLinha, false);
            }
            adicionarLinhaDre(table, "(=) Total de Saídas", totalSaidas.negate(), fontLinhaNegrito, true);

            adicionarLinhaDre(table, "(=) Saldo Final do Período", saldoFinal, fontResultado, true);

            document.add(table);

            Font fontAviso = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);
            Paragraph aviso = new Paragraph(
                    "\n\nEsta DFC é derivada do livro-caixa do sistema (vendas não-fiado, recebimentos de fiado, " +
                    "pagamentos de despesa e lançamentos manuais de aporte/retirada de sócio). Ela não segue " +
                    "formalmente a separação contábil em atividades Operacionais/Investimento/Financiamento — " +
                    "para a DFC oficial, consulte o contador responsável.",
                    fontAviso);
            document.add(aviso);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private String formatarOrigemCaixa(String origemEnum) {
        return switch (origemEnum) {
            case "VENDA_PDV" -> "Vendas (à vista/cartão/PIX)";
            case "RECEBIMENTO_FIADO" -> "Recebimento de Fiado";
            case "PAGAMENTO_DESPESA" -> "Pagamento de Despesas";
            case "APORTE_SOCIO" -> "Aporte de Sócio";
            case "RETIRADA_SOCIO" -> "Retirada de Sócio";
            default -> "Outro";
        };
    }

    /**
     * Balanço Patrimonial simplificado, na data de hoje (o Balanço é sempre
     * uma fotografia de um instante, não de um período — por isso não tem
     * filtro de data como os outros relatórios).
     *
     * Ativo = Passivo + Patrimônio Líquido, na teoria — aqui os dois lados
     * são calculados de forma INDEPENDENTE (um a partir do estoque/caixa/
     * contas a receber reais, o outro a partir de capital social + resultado
     * histórico acumulado), então uma pequena diferença de fechamento é
     * esperada e mostrada explicitamente, em vez de forçar os números a
     * baterem artificialmente. As causas mais prováveis de diferença:
     * despesas ainda não registradas no sistema, imobilizado sem
     * depreciação, ou o capital social não refletir exatamente o histórico
     * de aportes registrados no Caixa.
     */
    public byte[] gerarRelatorioBalancoPatrimonialPdf() {
        Empresa empresa = getEmpresaLogada();
        LocalDateTime inicioDosTempos = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime agora = LocalDateTime.now();

        // ==== ATIVO ====
        BigDecimal estoques = produtoRepository.calcularValorTotalEstoque(empresa.getId());
        BigDecimal contasAReceber = contaReceberRepository.somarContasAReceberEmAberto(empresa.getId());
        BigDecimal disponibilidades = movimentoCaixaRepository.calcularSaldoAtual(empresa.getId());
        BigDecimal ativoCirculante = estoques.add(contasAReceber).add(disponibilidades);

        BigDecimal imobilizado = produtoRepository.calcularValorImobilizado(empresa.getId());
        BigDecimal ativoNaoCirculante = imobilizado;

        BigDecimal ativoTotal = ativoCirculante.add(ativoNaoCirculante);

        // ==== PASSIVO ====
        BigDecimal contasAPagar = despesaRepository.somarContasAPagarEmAberto(empresa.getId());
        BigDecimal passivoCirculante = contasAPagar;

        // ==== PATRIMÔNIO LÍQUIDO ====
        BigDecimal capitalSocial = empresa.getCapitalSocial() != null ? empresa.getCapitalSocial() : BigDecimal.ZERO;

        // Lucros Acumulados = resultado operacional de TODO o histórico (mesma
        // fórmula do DRE, sem filtro de período): Receita − CMV − Despesas
        // Pagas − Perdas, desde o primeiro registro até hoje.
        BigDecimal receitaHistorica = movimentacaoRepository.somarFaturamentoNoIntervalo(empresa.getId(), inicioDosTempos, agora);
        BigDecimal cmvHistorico = movimentacaoRepository.somarCmvNoIntervalo(empresa.getId(), inicioDosTempos, agora);
        BigDecimal perdasHistoricas = movimentacaoRepository.somarPerdasNoIntervalo(empresa.getId(), inicioDosTempos, agora);
        BigDecimal despesasHistoricas = despesaRepository.somarDespesasPagasNoIntervalo(empresa.getId(), inicioDosTempos.toLocalDate(), agora.toLocalDate());
        BigDecimal lucrosAcumulados = receitaHistorica.subtract(cmvHistorico).subtract(despesasHistoricas).subtract(perdasHistoricas);

        BigDecimal patrimonioLiquido = capitalSocial.add(lucrosAcumulados);

        BigDecimal passivoMaisPl = passivoCirculante.add(patrimonioLiquido);
        BigDecimal diferencaFechamento = ativoTotal.subtract(passivoMaisPl);

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Balanço Patrimonial Simplificado - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            Paragraph subtitulo = new Paragraph("Posição em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            Font fontLinha = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fontLinhaNegrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font fontSecao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1.4f});
            table.setSpacingBefore(10);

            adicionarSecaoBalanco(table, "ATIVO", fontSecao);
            adicionarLinhaDre(table, "Ativo Circulante", ativoCirculante, fontLinhaNegrito, false);
            adicionarLinhaDre(table, "   Estoques", estoques, fontLinha, false);
            adicionarLinhaDre(table, "   Contas a Receber (Fiado em aberto)", contasAReceber, fontLinha, false);
            adicionarLinhaDre(table, "   Disponibilidades (Caixa)", disponibilidades, fontLinha, false);
            adicionarLinhaDre(table, "Ativo Não Circulante (Imobilizado)", ativoNaoCirculante, fontLinhaNegrito, false);
            adicionarLinhaDre(table, "(=) Ativo Total", ativoTotal, fontLinhaNegrito, true);

            adicionarSecaoBalanco(table, "PASSIVO", fontSecao);
            adicionarLinhaDre(table, "Passivo Circulante (Contas a Pagar em aberto)", passivoCirculante, fontLinhaNegrito, false);

            adicionarSecaoBalanco(table, "PATRIMÔNIO LÍQUIDO", fontSecao);
            adicionarLinhaDre(table, "   Capital Social", capitalSocial, fontLinha, false);
            adicionarLinhaDre(table, "   Lucros/Prejuízos Acumulados", lucrosAcumulados, fontLinha, false);
            adicionarLinhaDre(table, "(=) Patrimônio Líquido", patrimonioLiquido, fontLinhaNegrito, false);
            adicionarLinhaDre(table, "(=) Passivo + Patrimônio Líquido", passivoMaisPl, fontLinhaNegrito, true);

            document.add(table);

            Font fontDiferenca = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Paragraph diferenca = new Paragraph(
                    "\nDiferença de fechamento (Ativo − (Passivo + PL)): R$ " + String.format("%.2f", diferencaFechamento),
                    fontDiferenca);
            document.add(diferenca);

            Font fontAviso = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);
            Paragraph aviso = new Paragraph(
                    "\n\nBalanço Patrimonial simplificado — Ativo e Patrimônio Líquido são calculados de forma " +
                    "independente (um a partir do estoque/caixa/contas a receber reais, o outro a partir do capital " +
                    "social informado manualmente e do resultado histórico de vendas), então uma diferença de " +
                    "fechamento é esperada e não indica erro do sistema. O Imobilizado não tem depreciação calculada " +
                    "(fica sempre pelo valor de aquisição). Para o Balanço oficial, consulte o contador responsável.",
                    fontAviso);
            document.add(aviso);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private void adicionarSecaoBalanco(PdfPTable table, String titulo, Font fonte) {
        PdfPCell secao = new PdfPCell(new Phrase(titulo, fonte));
        secao.setColspan(2);
        secao.setBorder(Rectangle.NO_BORDER);
        secao.setPaddingTop(14);
        secao.setPaddingBottom(4);
        table.addCell(secao);
    }

    public byte[] gerarRelatorioPerdasPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();

        List<Movimentacao> perdas = movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(empresa.getId())
                .stream()
                .filter(m -> m.getTipo() == TipoMovimentacao.QUEBRA_PERDA)
                .collect(Collectors.toList());
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);
        if (limites != null) {
            perdas = perdas.stream()
                .filter(m -> m.getDataMovimentacao() != null && !m.getDataMovimentacao().isBefore(limites[0]) && !m.getDataMovimentacao().isAfter(limites[1]))
                .collect(Collectors.toList());
        }

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Relatorio Analitico de Quebras e Perdas", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            Paragraph subtitulo = new Paragraph("Empresa: " + empresa.getNomeFantasia().toUpperCase() + "\nGerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 3f, 1f, 1.5f, 2.5f});

            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Data", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Produto", fontCabecalho));
            PdfPCell h3 = new PdfPCell(new Phrase("Qtd", fontCabecalho));
            PdfPCell h4 = new PdfPCell(new Phrase("Custo Perdido", fontCabecalho));
            PdfPCell h5 = new PdfPCell(new Phrase("Motivo", fontCabecalho));

            h1.setBackgroundColor(Color.LIGHT_GRAY); h2.setBackgroundColor(Color.LIGHT_GRAY);
            h3.setBackgroundColor(Color.LIGHT_GRAY); h4.setBackgroundColor(Color.LIGHT_GRAY);
            h5.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(h1); table.addCell(h2); table.addCell(h3); table.addCell(h4); table.addCell(h5);

            DateTimeFormatter formatadorData = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            BigDecimal prejuizoTotal = BigDecimal.ZERO;

            for (Movimentacao m : perdas) {
                table.addCell(m.getDataMovimentacao().format(formatadorData));
                table.addCell(m.getProduto() != null ? m.getProduto().getNome() : "-");
                table.addCell(String.valueOf(m.getQuantidade()));

                BigDecimal precoCusto = m.getProduto() != null && m.getProduto().getPrecoCusto() != null ? m.getProduto().getPrecoCusto() : BigDecimal.ZERO;
                BigDecimal valorPerda = precoCusto.multiply(new BigDecimal(m.getQuantidade()));
                prejuizoTotal = prejuizoTotal.add(valorPerda);

                table.addCell("R$ " + String.format("%.2f", valorPerda));
                table.addCell(m.getMotivo() != null ? m.getMotivo() : "Não informado");
            }

            document.add(table);

            Font fontPrejuizo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.RED);
            Paragraph resumo = new Paragraph("\nPREJUIZO TOTAL ACUMULADO: R$ " + String.format("%.2f", prejuizoTotal), fontPrejuizo);
            resumo.setAlignment(Element.ALIGN_RIGHT);
            document.add(resumo);

            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }


  // CUPOM FISCAL DE BOBINA
    public byte[] gerarCupomFiscalPdf(Long movimentacaoId) {
        Movimentacao mov = movimentacaoRepository.findById(movimentacaoId).orElseThrow();
        List<Movimentacao> lista = new ArrayList<>(); lista.add(mov);
        return gerarCupomLoteInterno(lista, mov.getChaveNotaFiscal() != null ? mov.getChaveNotaFiscal() : "00000000000000");
    }

    public byte[] gerarCupomLotePdf(String chaveNotaFiscal) {
        List<Movimentacao> movs = movimentacaoRepository.findByChaveNotaFiscal(chaveNotaFiscal);
        if (movs.isEmpty()) throw new RecursoNaoEncontradoException("Nota não encontrada.");
        return gerarCupomLoteInterno(movs, chaveNotaFiscal);
    }

    private byte[] gerarCupomLoteInterno(List<Movimentacao> movs, String chaveNotaFiscal) {
        Empresa empresa = getEmpresaLogada();
        Rectangle bobina = new Rectangle(226, 1200);
        Document document = new Document(bobina, 10, 10, 10, 10);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new MarcaDaguaSemValorFiscal()); // 🔴 NOVO
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.COURIER_BOLD, 10);
            Font fontNormal = FontFactory.getFont(FontFactory.COURIER, 7);
            Font fontNegrito = FontFactory.getFont(FontFactory.COURIER_BOLD, 7);
            Font fontMicro = FontFactory.getFont(FontFactory.COURIER, 6);

            document.add(new Paragraph(empresa.getNomeFantasia().toUpperCase() + "\nCNPJ: " + (empresa.getCnpj() != null ? empresa.getCnpj() : "N/I") + "\n----------------------------------------", fontNormal));
            document.add(new Paragraph("CUPOM FISCAL ELETRONICO - SAT", fontNegrito));
            document.add(new Paragraph("----------------------------------------\nDATA: " + movs.get(0).getDataMovimentacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n----------------------------------------", fontNormal));
            document.add(new Paragraph("#|COD|DESC|QTD|UN|VL UN R$|VL TOT R$", fontNegrito));

            BigDecimal totalGeral = BigDecimal.ZERO;
            BigDecimal percTotal = BigDecimal.ZERO;

            for (Movimentacao m : movs) {
                Produto p = m.getProduto();
                BigDecimal precoUnit = (p.getPrecoVenda() != null && m.getTipo() == TipoMovimentacao.SAIDA) ? p.getPrecoVenda() : (p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO);
                BigDecimal totalItem = precoUnit.multiply(new BigDecimal(m.getQuantidade()));
                totalGeral = totalGeral.add(totalItem);

                String cod = p.getCodigoBarras() != null && p.getCodigoBarras().length() > 5 ? p.getCodigoBarras().substring(0, 5) : "00000";
                document.add(new Paragraph("001 " + cod + " " + p.getNome().substring(0, Math.min(p.getNome().length(), 15)) + "\n        " + m.getQuantidade() + " " + (p.getUnidade() != null ? p.getUnidade() : "UN") + " X " + String.format("%.2f", precoUnit) + " = " + String.format("%.2f", totalItem), fontNormal));

                if (p.getImpostos() != null) {
                    for (var imp : p.getImpostos()) {
                        if(imp.getAliquota() != null) percTotal = percTotal.add(new BigDecimal(String.valueOf(imp.getAliquota())));
                    }
                }
            }

            document.add(new Paragraph("----------------------------------------", fontNormal));
            Paragraph totalText = new Paragraph("TOTAL R$         " + String.format("%.2f", totalGeral), fontTitulo);
            totalText.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalText);
            document.add(new Paragraph("----------------------------------------", fontNormal));

            BigDecimal impostoAprox = totalGeral.multiply(percTotal.divide(new BigDecimal(movs.size() > 0 ? movs.size() : 1), 2, RoundingMode.HALF_UP)).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            document.add(new Paragraph("TRIBUTOS INCIDENTES (LEI 12.741/12)\nValor aprox. de tributos R$ " + String.format("%.2f", impostoAprox), fontMicro));

            String chaveFmt = chaveNotaFiscal.replaceAll("(.{4})", "$1 ").trim();
            document.add(new Paragraph("----------------------------------------\nCHAVE DE ACESSO:\n" + chaveFmt + "\n\nOBRIGADO PELA PREFERENCIA", fontMicro));

            document.close();
        } catch (Exception e) { e.printStackTrace(); }
        return out.toByteArray();
    }



    // DANFE A4 OFICIAL
    public byte[] gerarDanfeSimplesPdf(Long movimentacaoId) {
        Movimentacao mov = movimentacaoRepository.findById(movimentacaoId).orElseThrow();
        List<Movimentacao> lista = new ArrayList<>(); lista.add(mov);
        String chave = mov.getChaveNotaFiscal() != null ? mov.getChaveNotaFiscal() : "35260312345678000199550010000001231000001234";
        return construirLayoutDanfe(lista, chave);
    }

    public byte[] gerarDanfeLotePdf(String chaveNotaFiscal) {
        List<Movimentacao> movs = movimentacaoRepository.findByChaveNotaFiscal(chaveNotaFiscal);
        if (movs.isEmpty()) throw new RecursoNaoEncontradoException("Nota não encontrada.");
        return construirLayoutDanfe(movs, chaveNotaFiscal);
    }

    private byte[] construirLayoutDanfe(List<Movimentacao> movs, String chave) {
        Empresa empresa = getEmpresaLogada();
        Movimentacao primeira = movs.get(0);

        Document document = new Document(PageSize.A4, 15, 15, 15, 15);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new MarcaDaguaSemValorFiscal()); 
            document.open();

            Font fMin = FontFactory.getFont(FontFactory.HELVETICA, 5f);
            Font fMicro = FontFactory.getFont(FontFactory.HELVETICA, 6f);
            Font fNormal = FontFactory.getFont(FontFactory.HELVETICA, 7f);
            Font fBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f);
            Font fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f);
            Font fBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f);

            // 1. CANHOTO
            PdfPTable canhoto = new PdfPTable(2);
            canhoto.setWidthPercentage(100);
            canhoto.setWidths(new float[]{8f, 2f});

            PdfPCell cRec = new PdfPCell(new Phrase("RECEBEMOS DE " + empresa.getNomeFantasia().toUpperCase() + " OS PRODUTOS/SERVIÇOS CONSTANTES NA NOTA FISCAL INDICADA AO LADO", fNormal));
            cRec.setPadding(4f); canhoto.addCell(cRec);

            PdfPCell cNf = new PdfPCell();
            cNf.addElement(new Paragraph("NF-e", fTitle));
            cNf.addElement(new Paragraph("Nº: 000.000.001\nSÉRIE: 1", fBold));
            cNf.setHorizontalAlignment(Element.ALIGN_CENTER); canhoto.addCell(cNf);

            PdfPCell cAss = new PdfPCell(new Phrase("DATA DE RECEBIMENTO                   IDENTIFICAÇÃO E ASSINATURA DO RECEBEDOR", fMin));
            cAss.setPaddingBottom(15f); canhoto.addCell(cAss);

            canhoto.addCell(new PdfPCell(new Phrase("DESTINATÁRIO\nCONSUMIDOR FINAL", fMin)));
            document.add(canhoto);
            document.add(new Paragraph(" "));

            // 2. EMITENTE E DANFE
            PdfPTable header = new PdfPTable(3);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{4f, 2f, 4f});

            PdfPCell cEmi = new PdfPCell();
            cEmi.addElement(new Paragraph(empresa.getNomeFantasia().toUpperCase(), fTitle));
            cEmi.addElement(new Paragraph(empresa.getRazaoSocial(), fNormal));
            String end = (empresa.getEndereco() != null ? empresa.getEndereco() : "") + " " + (empresa.getCidade() != null ? empresa.getCidade() : "") + " " + (empresa.getEstado() != null ? empresa.getEstado() : "");
            cEmi.addElement(new Paragraph(end, fNormal));
            cEmi.addElement(new Paragraph("CNPJ: " + (empresa.getCnpj() != null ? empresa.getCnpj() : "-"), fBold));
            header.addCell(cEmi);

            PdfPCell cDanfe = new PdfPCell();
            cDanfe.setHorizontalAlignment(Element.ALIGN_CENTER);
            cDanfe.addElement(new Paragraph("DANFE", fBig));
            cDanfe.addElement(new Paragraph("DOCUMENTO AUXILIAR DA NOTA FISCAL ELETRÔNICA", fMicro));
            cDanfe.addElement(new Paragraph(primeira.getTipo() == TipoMovimentacao.ENTRADA ? "0 - ENTRADA" : "1 - SAÍDA", fBold));
            cDanfe.addElement(new Paragraph("Nº 000.000.001\nSÉRIE: 1\nFOLHA: 1 de 1", fBold));
            header.addCell(cDanfe);

            PdfPCell cBar = new PdfPCell();
            cBar.setPadding(4f); 
            cBar.addElement(new Paragraph("CHAVE DE ACESSO", fMin));

            // CÓDIGO DE BARRAS NATIVO
            Barcode128 barcode = new Barcode128();
            // Apenas para não falhar se a chave for muito curta (completa com zeros à esquerda)
            String chaveAjustada = String.format("%44s", chave.replaceAll("[^0-9]", "")).replace(' ', '0');
            barcode.setCode(chaveAjustada);
            barcode.setFont(null); // 
            barcode.setBarHeight(30f); 
            Image imgBarcode = barcode.createImageWithBarcode(writer.getDirectContent(), null, null);
            imgBarcode.scaleToFit(190f, 40f); 
            imgBarcode.setAlignment(Element.ALIGN_CENTER);
            cBar.addElement(imgBarcode);

            String chaveFmt = chaveAjustada.replaceAll("(.{4})", "$1 ").trim();
            Paragraph pChave = new Paragraph(chaveFmt, fBold);
            pChave.setAlignment(Element.ALIGN_CENTER);
            cBar.addElement(pChave);

            Paragraph pCons = new Paragraph("Consulta de autenticidade no portal nacional da NF-e\nwww.nfe.fazenda.gov.br/portal", fMicro);
            pCons.setAlignment(Element.ALIGN_CENTER);
            cBar.addElement(pCons);
            header.addCell(cBar);
            document.add(header);

            // 3. NATUREZA DA OPERAÇÃO
            PdfPTable natOp = new PdfPTable(2);
            natOp.setWidthPercentage(100);
            natOp.setWidths(new float[]{6f, 4f});
            natOp.addCell(criarCelula("NATUREZA DA OPERAÇÃO", primeira.getMotivo() != null ? primeira.getMotivo() : "VENDA", fMin, fBold));
            natOp.addCell(criarCelula("PROTOCOLO DE AUTORIZAÇÃO DE USO", "141180000000000 - " + primeira.getDataMovimentacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")), fMin, fBold));
            natOp.addCell(criarCelula("INSCRIÇÃO ESTADUAL", "ISENTO", fMin, fBold));
            natOp.addCell(criarCelula("CNPJ", empresa.getCnpj() != null ? empresa.getCnpj() : "-", fMin, fBold));
            document.add(natOp);
            document.add(new Paragraph(" "));

            // 4. DESTINATÁRIO
            document.add(new Paragraph("DESTINATÁRIO / REMETENTE", fBold));
            PdfPTable dest = new PdfPTable(3);
            dest.setWidthPercentage(100);
            dest.setWidths(new float[]{6f, 2f, 2f});
            dest.addCell(criarCelula("NOME / RAZÃO SOCIAL", "CONSUMIDOR FINAL", fMin, fNormal));
            dest.addCell(criarCelula("CNPJ / CPF", "000.000.000-00", fMin, fNormal));
            dest.addCell(criarCelula("DATA DA EMISSÃO", primeira.getDataMovimentacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), fMin, fNormal));
            document.add(dest);
            document.add(new Paragraph(" "));

            // 5. CÁLCULO DO IMPOSTO
            document.add(new Paragraph("CÁLCULO DO IMPOSTO", fBold));
            BigDecimal totalNota = BigDecimal.ZERO;
            for (Movimentacao m : movs) {
                BigDecimal preco = (m.getProduto().getPrecoVenda() != null && m.getTipo() == TipoMovimentacao.SAIDA) ? m.getProduto().getPrecoVenda() : (m.getProduto().getPrecoCusto() != null ? m.getProduto().getPrecoCusto() : BigDecimal.ZERO);
                totalNota = totalNota.add(preco.multiply(new BigDecimal(m.getQuantidade())));
            }

            PdfPTable calc = new PdfPTable(6);
            calc.setWidthPercentage(100);
            calc.addCell(criarCelula("BASE DE CÁLCULO DO ICMS", "0,00", fMin, fNormal));
            calc.addCell(criarCelula("VALOR DO ICMS", "0,00", fMin, fNormal));
            calc.addCell(criarCelula("BASE DE CÁLC. ICMS ST", "0,00", fMin, fNormal));
            calc.addCell(criarCelula("VALOR DO ICMS ST", "0,00", fMin, fNormal));
            calc.addCell(criarCelula("VLR TOTAL DOS PRODUTOS", String.format("%.2f", totalNota), fMin, fBold));
            calc.addCell(criarCelula("VALOR TOTAL DA NOTA", String.format("%.2f", totalNota), fMin, fBold));
            document.add(calc);
            document.add(new Paragraph(" "));

            // 6. ITENS
            document.add(new Paragraph("DADOS DO PRODUTO / SERVIÇO", fBold));
            PdfPTable itens = new PdfPTable(11);
            itens.setWidthPercentage(100);
            itens.setWidths(new float[]{1.2f, 4f, 1f, 0.8f, 0.8f, 1f, 1.2f, 1.2f, 1f, 1f, 1f});

            String[] cabs = {"CÓD. PROD.", "DESCRIÇÃO DO PRODUTO/SERVIÇO", "NCM/SH", "CST", "CFOP", "UNID", "QUANT.", "VLR. UNIT.", "VLR. TOTAL", "ALIQ. ICMS", "VLR. ICMS"};
            for (String c : cabs) {
                PdfPCell h = new PdfPCell(new Phrase(c, fMin));
                h.setBackgroundColor(Color.LIGHT_GRAY);
                h.setHorizontalAlignment(Element.ALIGN_CENTER);
                itens.addCell(h);
            }

            for (Movimentacao m : movs) {
                Produto p = m.getProduto();
                BigDecimal preco = (p.getPrecoVenda() != null && m.getTipo() == TipoMovimentacao.SAIDA) ? p.getPrecoVenda() : (p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO);
                BigDecimal totalItem = preco.multiply(new BigDecimal(m.getQuantidade()));

                itens.addCell(new Phrase(p.getCodigoBarras() != null ? p.getCodigoBarras() : "000", fMicro));
                itens.addCell(new Phrase(p.getNome(), fMicro));
                itens.addCell(new Phrase(p.getNcm() != null ? p.getNcm() : "00000000", fMicro));
                itens.addCell(new Phrase("0102", fMicro));
                itens.addCell(new Phrase(p.getCfop() != null ? p.getCfop() : "5102", fMicro));
                itens.addCell(new Phrase(p.getUnidade() != null ? p.getUnidade() : "UN", fMicro));

                PdfPCell cq = new PdfPCell(new Phrase(String.valueOf(m.getQuantidade()), fMicro)); cq.setHorizontalAlignment(Element.ALIGN_RIGHT); itens.addCell(cq);
                PdfPCell cp = new PdfPCell(new Phrase(String.format("%.2f", preco), fMicro)); cp.setHorizontalAlignment(Element.ALIGN_RIGHT); itens.addCell(cp);
                PdfPCell ct = new PdfPCell(new Phrase(String.format("%.2f", totalItem), fMicro)); ct.setHorizontalAlignment(Element.ALIGN_RIGHT); itens.addCell(ct);

                itens.addCell(new Phrase("0,00", fMicro));
                itens.addCell(new Phrase("0,00", fMicro));
            }
            document.add(itens);
            document.add(new Paragraph(" "));

            // 7. DADOS ADICIONAIS
            document.add(new Paragraph("DADOS ADICIONAIS", fBold));
            PdfPTable obs = new PdfPTable(1);
            obs.setWidthPercentage(100);
            PdfPCell cObs = criarCelula("INFORMAÇÕES COMPLEMENTARES", "Documento emitido por ME ou EPP optante pelo Simples Nacional.\nValor Aproximado dos Tributos: Consulte a fonte IBPT.", fMin, fNormal);
            cObs.setMinimumHeight(40f);
            obs.addCell(cObs);
            document.add(obs);

            document.close();
        } catch (Exception e) { e.printStackTrace(); }
        return out.toByteArray();
    }

    private PdfPCell criarCelula(String titulo, String valor, Font fTitulo, Font fValor) {
        PdfPCell c = new PdfPCell();
        c.setPadding(3f);
        c.addElement(new Paragraph(titulo, fTitulo));
        Paragraph pVal = new Paragraph(valor != null ? valor : "", fValor);
        pVal.setSpacingBefore(2f);
        c.addElement(pVal);
        return c;
    }


    // ═══════════════════════════════════════════════════════════════════════════════
    //  RELATÓRIOS COM GRÁFICOS
    // ═══════════════════════════════════════════════════════════════════════════════

    public byte[] gerarRelatorioProdutosMaisMovimentadosPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        
        // Buscar movimentações no período
        List<Movimentacao> movimentacoes = movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(empresa.getId());
        
        //  Filtro de Datas Aplicado
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);
        if (limites != null) {
            movimentacoes = movimentacoes.stream()
                .filter(m -> m.getDataMovimentacao() != null && !m.getDataMovimentacao().isBefore(limites[0]) && !m.getDataMovimentacao().isAfter(limites[1]))
                .collect(Collectors.toList());
        }
        
        // Agrupar por produto e contar movimentações
        var produtosMovimentados = movimentacoes.stream()
            .collect(Collectors.groupingBy(
                m -> m.getProduto().getNome(),
                Collectors.counting()
            ))
            .entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(15)
            .collect(Collectors.toList());
        
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            
            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Produtos Mais Movimentados - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);
            
            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            // Antes mostrava "Período:  a " em branco quando nenhum filtro de
            // data era escolhido — dataInicio/dataFim chegam como string vazia
            // do frontend (não null), então o ternário original (dataInicio != null
            // ? dataInicio : "Início") nunca caía no fallback.
            String textoPeríodo = (dataInicio != null && !dataInicio.isBlank() && dataFim != null && !dataFim.isBlank())
                    ? dataInicio + " a " + dataFim
                    : "Todo o histórico";
            Paragraph subtitulo = new Paragraph("Período: " + textoPeríodo + "\nGerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);
            
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            
            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Produto", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Qtd Movimentações", fontCabecalho));
            h1.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(h1);
            table.addCell(h2);
            
            for (var entry : produtosMovimentados) {
                table.addCell(entry.getKey());
                PdfPCell cellQtd = new PdfPCell(new Phrase(String.valueOf(entry.getValue())));
                cellQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellQtd);
            }

            document.add(table);

            // Tabela vazia sem nenhuma explicação parece bug pro usuário —
            // deixa explícito que realmente não há movimentação registrada
            // pra essa empresa/período, em vez de uma página em branco.
            if (produtosMovimentados.isEmpty()) {
                Paragraph semDados = new Paragraph(
                        "\nNenhuma movimentação de estoque encontrada" +
                        (limites != null ? " no período selecionado." : " para esta empresa."));
                semDados.setAlignment(Element.ALIGN_CENTER);
                document.add(semDados);
            }

            document.close();
            
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    public byte[] gerarRelatorioEstoqueCategoriaPdf(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        List<Produto> produtosDaEmpresa = produtoRepository.findByEmpresaId(empresa.getId());
        
// Agrupar por categoria e somar quantidades
        var estoquePorCategoria = produtosDaEmpresa.stream()
            .collect(Collectors.groupingBy(
                p -> (p.getCategoria() != null && !p.getCategoria().trim().isEmpty()) ? p.getCategoria() : "Sem Categoria",
                Collectors.summingInt(p -> p.getQuantidade() != null ? p.getQuantidade() : 0)
            ))
            .entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
        
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            
            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("Estoque por Categoria - " + empresa.getNomeFantasia().toUpperCase(), fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);
            
            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            Paragraph subtitulo = new Paragraph("Gerado em: " + dataHora + "\n\n");
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);
            
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            
            Font fontCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell h1 = new PdfPCell(new Phrase("Categoria", fontCabecalho));
            PdfPCell h2 = new PdfPCell(new Phrase("Quantidade Total", fontCabecalho));
            h1.setBackgroundColor(Color.LIGHT_GRAY);
            h2.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(h1);
            table.addCell(h2);
            
            int totalGeral = 0;
            for (var entry : estoquePorCategoria) {
                table.addCell(entry.getKey());
                PdfPCell cellQtd = new PdfPCell(new Phrase(String.valueOf(entry.getValue())));
                cellQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellQtd);
                totalGeral += entry.getValue();
            }
            
            // Linha de total
            Font fontTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            PdfPCell cellTotal = new PdfPCell(new Phrase("TOTAL", fontTotal));
            cellTotal.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(cellTotal);
            PdfPCell cellTotalQtd = new PdfPCell(new Phrase(String.valueOf(totalGeral), fontTotal));
            cellTotalQtd.setBackgroundColor(Color.LIGHT_GRAY);
            cellTotalQtd.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cellTotalQtd);
            
            document.add(table);
            document.close();
            
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    // 📊 Retorna os dados para o gráfico de Produtos Mais Movimentados (JSON)
    public List<Map<String, Object>> getProdutosMaisMovimentados(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        List<Movimentacao> movimentacoes = movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(empresa.getId());

       // Filtro de Datas Aplicado
        LocalDateTime[] limites = validarEParsearDatas(dataInicio, dataFim);
        if (limites != null) {
            movimentacoes = movimentacoes.stream()
                .filter(m -> m.getDataMovimentacao() != null && !m.getDataMovimentacao().isBefore(limites[0]) && !m.getDataMovimentacao().isAfter(limites[1]))
                .collect(Collectors.toList());
        }
        Map<String, Long> contagem = movimentacoes.stream()
                .filter(m -> m.getProduto() != null && m.getProduto().getNome() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getProduto().getNome(),
                        Collectors.counting()
                ));

        return contagem.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(15)
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("nome", entry.getKey());
                    map.put("quantidade", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // 📊 Retorna os dados para o gráfico de Estoque por Categoria (JSON)
    public List<Map<String, Object>> getEstoqueCategoria(String dataInicio, String dataFim) {
        Empresa empresa = getEmpresaLogada();
        List<Produto> produtosDaEmpresa = produtoRepository.findByEmpresaId(empresa.getId());

        Map<String, Integer> estoquePorCategoria = produtosDaEmpresa.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getCategoria() != null && !p.getCategoria().trim().isEmpty()) ? p.getCategoria() : "Sem Categoria",
                        Collectors.summingInt(p -> p.getQuantidade() != null ? p.getQuantidade() : 0)
                ));

        return estoquePorCategoria.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("categoria", entry.getKey());
                    map.put("quantidade", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }
}