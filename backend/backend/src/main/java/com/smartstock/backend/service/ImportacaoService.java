package com.smartstock.backend.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.model.Imposto;
import com.smartstock.backend.model.Lote;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.NotaFiscalImportada;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.repository.LoteRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.NotaFiscalImportadaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ImportacaoService {

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;
    private final LoteRepository loteRepository;
    private final FornecedorRepository fornecedorRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final NotaFiscalImportadaRepository notaFiscalImportadaRepository;

    public ImportacaoService(ProdutoRepository produtoRepository,
                             EmpresaRepository empresaRepository,
                             LoteRepository loteRepository,
                             FornecedorRepository fornecedorRepository,
                             MovimentacaoRepository movimentacaoRepository,
                             NotaFiscalImportadaRepository notaFiscalImportadaRepository) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.loteRepository = loteRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.notaFiscalImportadaRepository = notaFiscalImportadaRepository;
    }

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    // =========================================================
    //  RELATÓRIO DE IMPORTAÇÃO
    //  Nenhuma importação deve falhar "em silêncio": tudo que for
    //  ignorado ou não encontrado (fornecedor inexistente, linha
    //  malformada, etc.) vira um aviso explícito no retorno.
    // =========================================================
    private static class RelatorioImportacao {
        List<String> nomesNovos = new ArrayList<>();
        int totalAtualizados = 0;
        List<String> avisos = new ArrayList<>();

        String montarMensagem(String origem) {
            StringBuilder msg = new StringBuilder();
            msg.append("Processamento (" + origem + ") concluído!\n");
            msg.append("- Produtos atualizados no estoque: ").append(totalAtualizados).append("\n");
            msg.append("- Novos produtos cadastrados: ").append(nomesNovos.size()).append("\n");
            if (!nomesNovos.isEmpty()) {
                msg.append("\nLista de produtos recém-adicionados:\n");
                msg.append(String.join(", ", nomesNovos)).append("\n");
            }
            if (!avisos.isEmpty()) {
                msg.append("\nAvisos (").append(avisos.size()).append("):\n");
                msg.append(String.join("\n", avisos));
            }
            return msg.toString();
        }
    }

    // =========================================================
    //  LÓGICA 1: PLANILHAS (CSV) - Atualização e Catálogo
    // =========================================================
    @Transactional
    public String processarFicheiro(MultipartFile ficheiro) throws Exception {
        String nomeFicheiro = ficheiro.getOriginalFilename();

        if (nomeFicheiro == null || !nomeFicheiro.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Formato não suportado nesta rota. Envie apenas arquivos CSV.");
        }

        RelatorioImportacao relatorio = new RelatorioImportacao();
        List<ProdutoDTO> produtosLidos = lerCsv(ficheiro, relatorio.avisos);

        Long tenantIdAtual = getEmpresaIdLogada();
        Empresa empresaLogada = empresaRepository.findById(tenantIdAtual)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada no contexto de segurança."));

        // --- Pré-carrega tudo que vai ser consultado repetidamente, para não fazer
        //     1 query por linha do CSV (N+1). Empresas pequenas/médias têm poucos
        //     milhares de produtos/fornecedores no máximo, cabe tranquilo em memória
        //     durante a transação de importação. ---
        Map<String, Produto> produtosPorCodigoBarras = new HashMap<>();
        Map<String, Produto> produtosPorNome = new HashMap<>();
        for (Produto p : produtoRepository.findByEmpresaId(tenantIdAtual)) {
            if (p.getCodigoBarras() != null && !p.getCodigoBarras().isBlank()) {
                produtosPorCodigoBarras.put(p.getCodigoBarras(), p);
            }
            produtosPorNome.put(p.getNome(), p);
        }
        Map<String, Fornecedor> fornecedoresPorCnpj = new HashMap<>();
        Map<String, Fornecedor> fornecedoresPorNome = new HashMap<>();
        for (Fornecedor f : fornecedorRepository.findByEmpresaId(tenantIdAtual)) {
            if (f.getCnpj() != null && !f.getCnpj().isBlank()) {
                fornecedoresPorCnpj.put(normalizarCnpj(f.getCnpj()), f);
            }
            if (f.getNome() != null && !f.getNome().isBlank()) {
                fornecedoresPorNome.put(f.getNome().trim().toLowerCase(), f);
            }
        }

        for (ProdutoDTO pLido : produtosLidos) {
            if (pLido.getNome() == null || pLido.getNome().trim().isEmpty()) {
                relatorio.avisos.add("Linha ignorada: produto sem nome.");
                continue;
            }

            Optional<Produto> produtoExistente = Optional.empty();
            if (pLido.getCodigoBarras() != null && !pLido.getCodigoBarras().trim().isEmpty()) {
                produtoExistente = Optional.ofNullable(produtosPorCodigoBarras.get(pLido.getCodigoBarras().trim()));
            }
            if (produtoExistente.isEmpty()) {
                produtoExistente = Optional.ofNullable(produtosPorNome.get(pLido.getNome()));
            }

            Integer quantidadeLida = (pLido.getQuantidade() != null && pLido.getQuantidade() > 0) ? pLido.getQuantidade() : 0;

            Fornecedor fornecedorResolvido = resolverOuCriarFornecedorCsv(
                    pLido.getFornecedorNome(), pLido.getFornecedorCnpj(),
                    fornecedoresPorCnpj, fornecedoresPorNome, empresaLogada, relatorio.avisos);
            List<Imposto> impostosLidos = montarImpostos(pLido.getIcms(), pLido.getIpi(), pLido.getPis(), pLido.getCofins());

            Produto pSalvo;
            if (produtoExistente.isPresent()) {
                Produto pBase = produtoExistente.get();
                Integer quantidadeAtual = (pBase.getQuantidade() != null) ? pBase.getQuantidade() : 0;

                pBase.setQuantidade(quantidadeAtual + quantidadeLida);
                pBase.setNome(pLido.getNome());

                if (pLido.getPrecoCusto() != null) pBase.setPrecoCusto(pLido.getPrecoCusto());
                if (pLido.getPrecoVenda() != null) pBase.setPrecoVenda(pLido.getPrecoVenda());
                if (pLido.getCodigoBarras() != null) pBase.setCodigoBarras(pLido.getCodigoBarras());
                if (pLido.getCategoria() != null) pBase.setCategoria(pLido.getCategoria());
                if (pLido.getNcm() != null) pBase.setNcm(pLido.getNcm());
                if (fornecedorResolvido != null) pBase.setFornecedor(fornecedorResolvido);
                if (!impostosLidos.isEmpty()) pBase.setImpostos(impostosLidos);

                pSalvo = produtoRepository.save(pBase);
                relatorio.totalAtualizados++;

                if (quantidadeLida > 0) {
                    criarLoteInicial(pSalvo, quantidadeLida);
                    registrarMovimentacaoImportacao(pSalvo, empresaLogada, quantidadeLida, "Entrada via importação de CSV");
                }

            } else {
                Produto pNovo = new Produto();
                pNovo.setNome(pLido.getNome());
                pNovo.setDescricao(pLido.getDescricao());
                pNovo.setPrecoCusto(pLido.getPrecoCusto());
                pNovo.setPrecoVenda(pLido.getPrecoVenda());
                pNovo.setCodigoBarras(pLido.getCodigoBarras());
                pNovo.setCategoria(pLido.getCategoria());
                pNovo.setQuantidade(pLido.getQuantidade());
                pNovo.setEstoqueMinimo(pLido.getQuantidadeMinima() != null ? pLido.getQuantidadeMinima() : 5);
                pNovo.setNcm(pLido.getNcm());
                pNovo.setUnidade(pLido.getUnidade());
                pNovo.setEmpresa(empresaLogada);
                pNovo.setFornecedor(fornecedorResolvido);
                pNovo.setImpostos(impostosLidos);

                pSalvo = produtoRepository.save(pNovo);
                relatorio.nomesNovos.add(pSalvo.getNome());

                if (quantidadeLida > 0) {
                    criarLoteInicial(pSalvo, quantidadeLida);
                    registrarMovimentacaoImportacao(pSalvo, empresaLogada, quantidadeLida, "Estoque inicial - Importação de CSV");
                }
            }

            // Mantém os mapas atualizados: se o mesmo código de barras aparecer de novo
            // mais à frente no MESMO arquivo, a linha seguinte deve atualizar este
            // registro em vez de tentar criar um duplicado (violaria a constraint única).
            if (pSalvo.getCodigoBarras() != null && !pSalvo.getCodigoBarras().isBlank()) {
                produtosPorCodigoBarras.put(pSalvo.getCodigoBarras(), pSalvo);
            }
            produtosPorNome.put(pSalvo.getNome(), pSalvo);
        }

        return relatorio.montarMensagem("CSV");
    }

    /**
     * Lê o CSV linha a linha COM tolerância a erros: uma linha malformada não derruba
     * o arquivo inteiro (withThrowExceptions(false)) — ela só vira um aviso no relatório.
     */
    private List<ProdutoDTO> lerCsv(MultipartFile ficheiro, List<String> avisos) throws Exception {
        List<String> linhasLimpas = new ArrayList<>();
        char separador = ',';
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            boolean primeiraLinha = true;
            int numColunasCabecalho = 0;
            int numeroLinha = 0;
            while ((linha = br.readLine()) != null) {
                numeroLinha++;
                if (linha.trim().isEmpty()) continue;
                if (primeiraLinha) {
                    if (linha.contains(";")) separador = ';';
                    numColunasCabecalho = linha.split(String.valueOf(separador), -1).length;
                    linhasLimpas.add(linha);
                    primeiraLinha = false;
                } else {
                    if (linha.split(String.valueOf(separador), -1).length == numColunasCabecalho) {
                        linhasLimpas.add(linha);
                    } else {
                        avisos.add("Linha " + numeroLinha + " do CSV ignorada: número de colunas diferente do cabeçalho.");
                    }
                }
            }
        }
        String csvLimpo = String.join("\n", linhasLimpas);
        try (Reader reader = new java.io.StringReader(csvLimpo)) {
            CsvToBean<ProdutoDTO> csvToBean = new CsvToBeanBuilder<ProdutoDTO>(reader)
                    .withType(ProdutoDTO.class)
                    .withSeparator(separador)
                    .withThrowExceptions(false)
                    .build();
            List<ProdutoDTO> produtos = csvToBean.parse();
            for (CsvException erro : csvToBean.getCapturedExceptions()) {
                avisos.add("Linha " + erro.getLineNumber() + " do CSV ignorada: " + erro.getMessage());
            }
            return produtos;
        }
    }

    // =========================================================
    //  LÓGICA 2: LEITURA OFICIAL DE NFe (XML DA SEFAZ)
    // =========================================================

    public Map<String, Object> extrairDadosDoXmlSefaz(MultipartFile file) {
        List<Map<String, Object>> produtosEncontrados = new ArrayList<>();
        Map<String, Object> fornecedorEncontrado = new HashMap<>();
        Map<String, Object> notaFiscalInfo = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.getInputStream());
            doc.getDocumentElement().normalize();

            // --- IDENTIFICAÇÃO DA NOTA (chave de acesso, usada pra bloquear reimportação) ---
            NodeList infNFeList = doc.getElementsByTagNameNS("*", "infNFe");
            if (infNFeList.getLength() > 0) {
                Element infNFe = (Element) infNFeList.item(0);
                String idAttr = infNFe.getAttribute("Id"); // formato: "NFe" + 44 dígitos
                if (idAttr != null && idAttr.length() >= 44) {
                    String chave = idAttr.replaceFirst("(?i)^NFe", "").trim();
                    notaFiscalInfo.put("chaveAcesso", chave);
                }
            }
            notaFiscalInfo.put("numeroNota", getTagValue("nNF", doc.getDocumentElement()));
            notaFiscalInfo.put("serie", getTagValue("serie", doc.getDocumentElement()));
            notaFiscalInfo.put("dataEmissao", getTagValue("dhEmi", doc.getDocumentElement()));
            notaFiscalInfo.put("valorTotal", parseBigDecimalSeguro(getTagValue("vNF", doc.getDocumentElement())));
            notaFiscalInfo.put("nomeArquivo", file.getOriginalFilename());

            // --- FORNECEDOR (tag <emit>, aparece 1x por nota, fora do loop de produtos) ---
            NodeList emitList = doc.getElementsByTagNameNS("*", "emit");
            if (emitList.getLength() > 0) {
                Element emit = (Element) emitList.item(0);
                String cnpjEmit = getTagValue("CNPJ", emit);
                if (cnpjEmit != null && !cnpjEmit.trim().isEmpty()) {
                    fornecedorEncontrado.put("cnpj", cnpjEmit);
                    fornecedorEncontrado.put("nome", getTagValue("xNome", emit));
                    fornecedorEncontrado.put("telefone", getTagValue("fone", emit));
                    fornecedorEncontrado.put("email", getTagValue("email", emit));

                    String enderecoCompleto = String.join(", ", java.util.stream.Stream.of(
                                    getTagValue("xLgr", emit),
                                    getTagValue("nro", emit),
                                    getTagValue("xBairro", emit),
                                    getTagValue("xMun", emit),
                                    getTagValue("UF", emit))
                            .filter(s -> s != null && !s.trim().isEmpty())
                            .toArray(String[]::new));
                    fornecedorEncontrado.put("endereco", enderecoCompleto);
                }
            }

            // --- PRODUTOS + IMPOSTOS (tag <det>, 1x por item da nota) ---
            NodeList nList = doc.getElementsByTagNameNS("*", "det");

            for (int i = 0; i < nList.getLength(); i++) {
                Element element = (Element) nList.item(i);
                Element prod = (Element) element.getElementsByTagNameNS("*", "prod").item(0);

                Map<String, Object> produto = new HashMap<>();

                String ean = getTagValue("cEAN", prod);
                if (ean == null || ean.equals("SEM GTIN") || ean.trim().isEmpty()) {
                    ean = getTagValue("cProd", prod);
                }
                produto.put("codigoBarras", ean);
                produto.put("nome", getTagValue("xProd", prod));
                produto.put("ncm", getTagValue("NCM", prod));

                String qtdStr = getTagValue("qCom", prod);
                String precoStr = getTagValue("vUnCom", prod);

                produto.put("quantidade", (int) Math.round(Double.parseDouble(qtdStr)));
                // Mantém a precisão usando BigDecimal já na extração
                produto.put("precoCusto", new BigDecimal(precoStr));

                // Tag <imposto> fica dentro de <det>, irmã de <prod>. getTagValue já busca
                // recursivamente, então não importa em qual sub-grupo (ICMS00, ICMS60...) a
                // alíquota está. Extraímos como BigDecimal para reaproveitar o MESMO método
                // montarImpostos(...) usado pela importação de CSV — uma única regra de
                // negócio para "virar Imposto", em vez de duas implementações divergentes.
                Element impostoEl = (Element) element.getElementsByTagNameNS("*", "imposto").item(0);
                if (impostoEl != null) {
                    produto.put("icms", parseBigDecimalSeguro(getTagValue("pICMS", impostoEl)));
                    produto.put("ipi", parseBigDecimalSeguro(getTagValue("pIPI", impostoEl)));
                    produto.put("pis", parseBigDecimalSeguro(getTagValue("pPIS", impostoEl)));
                    produto.put("cofins", parseBigDecimalSeguro(getTagValue("pCOFINS", impostoEl)));
                }

                produtosEncontrados.add(produto);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao ler o XML da Nota Fiscal: Certifique-se que é uma NF-e válida da SEFAZ.");
        }

        notaFiscalInfo.put("quantidadeItens", produtosEncontrados.size());

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("fornecedor", fornecedorEncontrado);
        resultado.put("produtos", produtosEncontrados);
        resultado.put("notaFiscal", notaFiscalInfo);
        return resultado;
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagNameNS("*", tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }

    private BigDecimal parseBigDecimalSeguro(String valor) {
        if (valor == null || valor.trim().isEmpty()) return null;
        try {
            return new BigDecimal(valor.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public String salvarProdutosLidos(Map<String, Object> dadosLidos) {
        Long empresaId = getEmpresaIdLogada();
        Empresa empresaLogada = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada."));

        RelatorioImportacao relatorio = new RelatorioImportacao();

        // --- BLOQUEIO DE REIMPORTAÇÃO: se a chave de acesso desta NFe já foi
        //     importada antes por esta empresa, aborta ANTES de tocar em qualquer
        //     produto. Isso evita duplicar estoque por reenvio acidental do XML. ---
        Map<String, Object> dadosNota = (Map<String, Object>) dadosLidos.get("notaFiscal");
        String chaveAcesso = dadosNota != null ? (String) dadosNota.get("chaveAcesso") : null;

        if (chaveAcesso != null && !chaveAcesso.isBlank()) {
            notaFiscalImportadaRepository.findByChaveAcessoAndEmpresaId(chaveAcesso, empresaId)
                    .ifPresent(notaExistente -> {
                        throw new NotaFiscalDuplicadaException(
                                "Esta NFe (chave " + chaveAcesso + ") já foi importada em "
                                        + notaExistente.getDataImportacao()
                                        + ". Para evitar duplicar o estoque, o arquivo não foi processado novamente.");
                    });
        } else {
            relatorio.avisos.add("Não foi possível extrair a chave de acesso desta NFe — a proteção contra reimportação duplicada não pôde ser aplicada para este arquivo.");
        }

        // Resolve (ou cadastra) o fornecedor uma única vez por nota
        Map<String, Object> dadosFornecedor = (Map<String, Object>) dadosLidos.get("fornecedor");
        Fornecedor fornecedor = resolverOuCriarFornecedorPorCnpj(dadosFornecedor, empresaLogada);
        if (fornecedor == null) {
            relatorio.avisos.add("Não foi possível identificar o fornecedor (tag <emit>/CNPJ ausente ou inválida no XML). Produtos importados sem vínculo de fornecedor.");
        }

        List<Map<String, Object>> produtosLidos = (List<Map<String, Object>>) dadosLidos.get("produtos");

        // Pré-carrega em lote os produtos já existentes com os códigos de barras da nota,
        // em vez de 1 query por item (mesma lógica de performance usada no CSV).
        List<String> codigos = produtosLidos.stream()
                .map(m -> (String) m.get("codigoBarras"))
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Produto> produtosPorCodigoBarras = codigos.isEmpty()
                ? new HashMap<>()
                : produtoRepository.findByCodigoBarrasInAndEmpresaId(codigos, empresaId).stream()
                        .collect(Collectors.toMap(Produto::getCodigoBarras, p -> p, (a, b) -> a));

        for (Map<String, Object> map : produtosLidos) {
            String codigoBarras = map.get("codigoBarras").toString();
            String nome = map.get("nome").toString();
            Integer quantidade = Integer.parseInt(map.get("quantidade").toString());
            // Lê como BigDecimal para não perder cêntimos
            BigDecimal precoCusto = new BigDecimal(map.get("precoCusto").toString());
            String ncm = map.containsKey("ncm") ? map.get("ncm").toString() : null;
            List<Imposto> impostos = montarImpostos(
                    (BigDecimal) map.get("icms"), (BigDecimal) map.get("ipi"),
                    (BigDecimal) map.get("pis"), (BigDecimal) map.get("cofins"));

            Optional<Produto> produtoExistente = Optional.ofNullable(produtosPorCodigoBarras.get(codigoBarras));

            Produto pSalvo;
            if (produtoExistente.isPresent()) {
                Produto p = produtoExistente.get();
                // 1. Atualiza Quantidade (Estoque)
                p.setQuantidade((p.getQuantidade() != null ? p.getQuantidade() : 0) + quantidade);
                // 2. Atualiza Preço de Custo baseado na Nota Fiscal
                if (precoCusto.compareTo(BigDecimal.ZERO) > 0) {
                    p.setPrecoCusto(precoCusto);
                }
                if ((p.getNcm() == null || p.getNcm().isEmpty()) && ncm != null) {
                    p.setNcm(ncm);
                }
                // 3. Vincula o fornecedor da nota (a NFe é a fonte mais confiável)
                if (fornecedor != null) {
                    p.setFornecedor(fornecedor);
                }
                // 4. Atualiza os impostos com os valores desta nota
                if (!impostos.isEmpty()) {
                    p.setImpostos(impostos);
                }
                pSalvo = produtoRepository.save(p);
                relatorio.totalAtualizados++;
                if (quantidade > 0) {
                    criarLoteInicial(pSalvo, quantidade);
                    registrarMovimentacaoImportacao(pSalvo, empresaLogada, quantidade, "Entrada via importação de XML NFe");
                }

            } else {
                Produto pNovo = new Produto();
                pNovo.setCodigoBarras(codigoBarras);
                pNovo.setNome(nome);
                pNovo.setQuantidade(quantidade);
                pNovo.setPrecoCusto(precoCusto);
                // Sugestão de venda: +50% do custo
                pNovo.setPrecoVenda(precoCusto.multiply(new BigDecimal("1.50")));
                pNovo.setEstoqueMinimo(5);
                pNovo.setCategoria("Importado NFe");
                pNovo.setNcm(ncm);
                pNovo.setUnidade("UN");
                pNovo.setEmpresa(empresaLogada);
                pNovo.setFornecedor(fornecedor);
                pNovo.setImpostos(impostos);

                pSalvo = produtoRepository.save(pNovo);
                relatorio.nomesNovos.add(pSalvo.getNome());
                if (quantidade > 0) {
                    criarLoteInicial(pSalvo, quantidade);
                    registrarMovimentacaoImportacao(pSalvo, empresaLogada, quantidade, "Estoque inicial - Importação de XML NFe");
                }
            }

            // Se a mesma nota trouxer o mesmo código de barras em mais de um <det>,
            // a próxima ocorrência deve atualizar este registro, não duplicar.
            if (pSalvo.getCodigoBarras() != null && !pSalvo.getCodigoBarras().isBlank()) {
                produtosPorCodigoBarras.put(pSalvo.getCodigoBarras(), pSalvo);
            }
        }

        if (fornecedor != null) {
            relatorio.avisos.add(0, "Fornecedor vinculado: " + fornecedor.getNome() + " (CNPJ " + fornecedor.getCnpj() + ").");
        }

        if (chaveAcesso != null && !chaveAcesso.isBlank()) {
            NotaFiscalImportada registro = new NotaFiscalImportada();
            registro.setChaveAcesso(chaveAcesso);
            registro.setNumeroNota((String) dadosNota.get("numeroNota"));
            registro.setSerie((String) dadosNota.get("serie"));
            registro.setDataEmissao((String) dadosNota.get("dataEmissao"));
            registro.setValorTotal((BigDecimal) dadosNota.get("valorTotal"));
            registro.setQuantidadeItens((Integer) dadosNota.get("quantidadeItens"));
            registro.setNomeArquivo((String) dadosNota.get("nomeArquivo"));
            registro.setEmpresa(empresaLogada);
            registro.setFornecedor(fornecedor);
            notaFiscalImportadaRepository.save(registro);
        }

        return relatorio.montarMensagem("XML NFe");
    }

    /**
     * Busca o fornecedor pelo CNPJ dentro da empresa logada; se não existir, cadastra
     * um novo com os dados extraídos da tag <emit> do XML.
     */
    private Fornecedor resolverOuCriarFornecedorPorCnpj(Map<String, Object> dadosFornecedor, Empresa empresaLogada) {
        if (dadosFornecedor == null || dadosFornecedor.isEmpty()) {
            return null;
        }
        Object cnpjObj = dadosFornecedor.get("cnpj");
        if (cnpjObj == null || cnpjObj.toString().trim().isEmpty()) {
            return null;
        }
        String cnpj = cnpjObj.toString().trim();

        return fornecedorRepository.findByCnpjAndEmpresaId(cnpj, empresaLogada.getId())
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCnpj(cnpj);
                    Object nomeObj = dadosFornecedor.get("nome");
                    novo.setNome(nomeObj != null && !nomeObj.toString().trim().isEmpty()
                            ? nomeObj.toString() : "Fornecedor importado via NFe");
                    Object telefoneObj = dadosFornecedor.get("telefone");
                    novo.setTelefone(telefoneObj != null ? telefoneObj.toString() : null);
                    Object emailObj = dadosFornecedor.get("email");
                    novo.setEmail(emailObj != null ? emailObj.toString() : null);
                    Object enderecoObj = dadosFornecedor.get("endereco");
                    novo.setEndereco(enderecoObj != null ? enderecoObj.toString() : null);
                    novo.setEmpresa(empresaLogada);
                    return fornecedorRepository.save(novo);
                });
    }

    /**
     * Resolve o fornecedor de uma linha do CSV a partir do nome e/ou CNPJ informados
     * (mapas pré-carregados, sem 1 query por linha). Se não encontrar um fornecedor
     * já cadastrado, CADASTRA um novo automaticamente — igual já acontece com os
     * produtos — em vez de só avisar e deixar o produto sem vínculo.
     *
     * Prioridade de busca/criação: CNPJ (chave mais confiável) e, na ausência dele,
     * o nome. Quando o fornecedor precisa ser criado só com nome (sem CNPJ na
     * planilha), o campo cnpj é obrigatório no banco — geramos um placeholder
     * "PENDENTE-XXXXXXXX" e avisamos no relatório para o gestor completar depois
     * em Fornecedores.
     */
    private Fornecedor resolverOuCriarFornecedorCsv(String nomeLido, String cnpjLido,
                                                      Map<String, Fornecedor> fornecedoresPorCnpj,
                                                      Map<String, Fornecedor> fornecedoresPorNome,
                                                      Empresa empresaLogada,
                                                      List<String> avisos) {
        String nome = (nomeLido != null) ? nomeLido.trim() : "";
        String cnpjDigitos = normalizarCnpj(cnpjLido);
        boolean cnpjInformadoEhValido = !cnpjDigitos.isEmpty() && cnpjValido(cnpjDigitos);

        if (!cnpjDigitos.isEmpty() && !cnpjInformadoEhValido) {
            avisos.add("CNPJ '" + cnpjLido + "' informado para o fornecedor '"
                    + (!nome.isEmpty() ? nome : "(sem nome)")
                    + "' é inválido (dígito verificador não confere) e foi ignorado; "
                    + "o fornecedor foi resolvido/cadastrado apenas pelo nome.");
        }

        if (nome.isEmpty() && !cnpjInformadoEhValido) {
            return null; // linha não informou um fornecedor utilizável — mesmo comportamento de antes
        }

        // 1) Busca/cria por CNPJ, se um CNPJ válido foi informado — é a chave mais confiável
        if (cnpjInformadoEhValido) {
            Fornecedor existente = fornecedoresPorCnpj.get(cnpjDigitos);
            if (existente != null) return existente;

            String cnpjFormatado = formatarCnpj(cnpjDigitos);
            Fornecedor novo = new Fornecedor();
            novo.setCnpj(cnpjFormatado); // salvo com a mesma máscara usada no cadastro manual
            novo.setNome(!nome.isEmpty() ? nome : "Fornecedor importado via CSV");
            novo.setEmpresa(empresaLogada);
            Fornecedor salvo = fornecedorRepository.save(novo);

            fornecedoresPorCnpj.put(cnpjDigitos, salvo);
            if (!salvo.getNome().isBlank()) fornecedoresPorNome.put(salvo.getNome().toLowerCase(), salvo);

            avisos.add("Fornecedor '" + salvo.getNome() + "' (CNPJ " + cnpjFormatado + ") não existia e foi cadastrado automaticamente.");
            return salvo;
        }

        // 2) Sem CNPJ válido na linha: busca/cria por nome (nome garantidamente não vazio aqui)
        Fornecedor existente = fornecedoresPorNome.get(nome.toLowerCase());
        if (existente != null) return existente;

        String cnpjPendente = "PENDENTE-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Fornecedor novo = new Fornecedor();
        novo.setNome(nome);
        novo.setCnpj(cnpjPendente);
        novo.setEmpresa(empresaLogada);
        Fornecedor salvo = fornecedorRepository.save(novo);

        fornecedoresPorNome.put(nome.toLowerCase(), salvo);
        fornecedoresPorCnpj.put(cnpjPendente, salvo);

        avisos.add("Fornecedor '" + nome + "' não existia e foi cadastrado automaticamente sem CNPJ "
                + "(a planilha não informou um válido). Edite o cadastro do fornecedor em 'Fornecedores' para completar o CNPJ real.");
        return salvo;
    }

    /** Mantém só os dígitos do CNPJ, pra comparar "12.345.678/0001-90" com "12345678000190". */
    private String normalizarCnpj(String cnpj) {
        if (cnpj == null) return "";
        return cnpj.replaceAll("\\D", "");
    }

    /**
     * Validação real de CNPJ (14 dígitos + dígitos verificadores), não só contagem
     * de caracteres — pega tanto erro de digitação quanto CNPJ "de mentira"
     * (sequências repetidas tipo 11111111111111) que passariam batido só checando o tamanho.
     */
    private boolean cnpjValido(String digitos) {
        if (digitos == null || digitos.length() != 14) return false;
        if (digitos.chars().distinct().count() == 1) return false;

        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int soma1 = 0;
        for (int i = 0; i < 12; i++) soma1 += (digitos.charAt(i) - '0') * pesos1[i];
        int resto1 = soma1 % 11;
        int dv1 = resto1 < 2 ? 0 : 11 - resto1;
        if (dv1 != (digitos.charAt(12) - '0')) return false;

        int soma2 = 0;
        for (int i = 0; i < 13; i++) soma2 += (digitos.charAt(i) - '0') * pesos2[i];
        int resto2 = soma2 % 11;
        int dv2 = resto2 < 2 ? 0 : 11 - resto2;
        return dv2 == (digitos.charAt(13) - '0');
    }

    /** Formata 14 dígitos já validados no padrão XX.XXX.XXX/XXXX-XX, igual ao cadastro manual. */
    private String formatarCnpj(String digitos) {
        return digitos.substring(0, 2) + "." + digitos.substring(2, 5) + "." + digitos.substring(5, 8)
                + "/" + digitos.substring(8, 12) + "-" + digitos.substring(12, 14);
    }

    /**
     * Ponto único de conversão "4 alíquotas -> List<Imposto>", reaproveitado
     * IGUALMENTE pela importação de CSV e pela de XML/NFe.
     */
    private List<Imposto> montarImpostos(BigDecimal icms, BigDecimal ipi, BigDecimal pis, BigDecimal cofins) {
        List<Imposto> impostos = new ArrayList<>();
        adicionarImpostoSeExistir(impostos, "ICMS", "Estadual", icms);
        adicionarImpostoSeExistir(impostos, "IPI", "Federal", ipi);
        adicionarImpostoSeExistir(impostos, "PIS", "Federal", pis);
        adicionarImpostoSeExistir(impostos, "COFINS", "Federal", cofins);
        return impostos;
    }

    private void adicionarImpostoSeExistir(List<Imposto> lista, String sigla, String esfera, BigDecimal aliquota) {
        if (aliquota != null) {
            Imposto imposto = new Imposto();
            imposto.setSigla(sigla);
            imposto.setEsfera(esfera);
            imposto.setAliquota(aliquota);
            lista.add(imposto);
        }
    }

    private void criarLoteInicial(Produto produto, Integer quantidade) {
        Lote lote = new Lote();
        lote.setProduto(produto);
        lote.setQuantidade(quantidade);
        lote.setDataValidade(LocalDate.now().plusYears(1));
        loteRepository.save(lote);
    }

    private void registrarMovimentacaoImportacao(Produto produto, Empresa empresa, Integer quantidade, String motivo) {
        Movimentacao mov = new Movimentacao();
        mov.setProduto(produto);
        mov.setEmpresa(empresa);
        mov.setTipo(TipoMovimentacao.ENTRADA);
        mov.setQuantidade(quantidade);
        mov.setMotivo(motivo);
        movimentacaoRepository.save(mov);
    }
}