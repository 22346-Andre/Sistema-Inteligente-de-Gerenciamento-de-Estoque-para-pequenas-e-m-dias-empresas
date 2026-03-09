package com.smartstock.backend.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.smartstock.backend.dto.FornecedorDTO;
import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.model.Lote;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.repository.LoteRepository;
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

@Service
public class ImportacaoService {

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;
    private final LoteRepository loteRepository;
    private final FornecedorRepository fornecedorRepository;

    public ImportacaoService(ProdutoRepository produtoRepository,
                             EmpresaRepository empresaRepository,
                             LoteRepository loteRepository,
                             FornecedorRepository fornecedorRepository) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.loteRepository = loteRepository;
        this.fornecedorRepository = fornecedorRepository;
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
    //  LÓGICA 1: PLANILHAS (CSV) - Atualização e Catálogo
    // =========================================================
    @Transactional
    public String processarFicheiro(MultipartFile ficheiro) throws Exception {
        String nomeFicheiro = ficheiro.getOriginalFilename();

        if (nomeFicheiro == null || !nomeFicheiro.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Formato não suportado nesta rota. Envie apenas arquivos CSV.");
        }

        List<ProdutoDTO> produtosLidos = lerCsv(ficheiro);
        Long tenantIdAtual = getEmpresaIdLogada();
        Empresa empresaLogada = empresaRepository.findById(tenantIdAtual)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada no contexto de segurança."));

        List<Produto> produtosNovos = new ArrayList<>();
        List<Produto> produtosAtualizados = new ArrayList<>();
        List<String> nomesAdicionados = new ArrayList<>();

        for (ProdutoDTO pLido : produtosLidos) {
            if (pLido.getNome() == null || pLido.getNome().trim().isEmpty()) continue;

            Optional<Produto> produtoExistente = Optional.empty();
            if (pLido.getCodigoBarras() != null && !pLido.getCodigoBarras().trim().isEmpty()) {
                produtoExistente = produtoRepository.findByCodigoBarrasAndEmpresaId(pLido.getCodigoBarras(), tenantIdAtual);
            }
            if (produtoExistente.isEmpty()) {
                produtoExistente = produtoRepository.findByNomeAndEmpresa(pLido.getNome(), empresaLogada);
            }

            Integer quantidadeLida = (pLido.getQuantidade() != null) ? pLido.getQuantidade() : 0;

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

                if (pLido.getFornecedorId() != null) {
                    fornecedorRepository.findById(pLido.getFornecedorId()).ifPresent(pBase::setFornecedor);
                }

                Produto pSalvo = produtoRepository.save(pBase);
                produtosAtualizados.add(pSalvo);

                if (quantidadeLida > 0) criarLoteInicial(pSalvo, quantidadeLida);

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

                if (pLido.getFornecedorId() != null) {
                    fornecedorRepository.findById(pLido.getFornecedorId()).ifPresent(pNovo::setFornecedor);
                }

                Produto pSalvo = produtoRepository.save(pNovo);
                produtosNovos.add(pSalvo);
                nomesAdicionados.add(pSalvo.getNome());

                if (quantidadeLida > 0) criarLoteInicial(pSalvo, quantidadeLida);
            }
        }

        StringBuilder mensagem = new StringBuilder();
        mensagem.append("Processamento concluído com sucesso! \n");
        mensagem.append("- Produtos atualizados no estoque: ").append(produtosAtualizados.size()).append("\n");
        mensagem.append("- Novos produtos cadastrados: ").append(produtosNovos.size()).append("\n");
        if (!nomesAdicionados.isEmpty()) {
            mensagem.append("\nLista de produtos recém-adicionados:\n");
            mensagem.append(String.join(", ", nomesAdicionados));
        }
        return mensagem.toString();
    }

    private List<ProdutoDTO> lerCsv(MultipartFile ficheiro) throws Exception {
        List<String> linhasLimpas = new ArrayList<>();
        char separador = ',';
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            boolean primeiraLinha = true;
            int numColunasCabecalho = 0;
            while ((linha = br.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;
                if (primeiraLinha) {
                    if (linha.contains(";")) separador = ';';
                    numColunasCabecalho = linha.split(String.valueOf(separador), -1).length;
                    linhasLimpas.add(linha);
                    primeiraLinha = false;
                } else {
                    if (linha.split(String.valueOf(separador), -1).length == numColunasCabecalho) {
                        linhasLimpas.add(linha);
                    }
                }
            }
        }
        String csvLimpo = String.join("\n", linhasLimpas);
        try (Reader reader = new java.io.StringReader(csvLimpo)) {
            CsvToBean<ProdutoDTO> csvToBean = new CsvToBeanBuilder<ProdutoDTO>(reader).withType(ProdutoDTO.class).withSeparator(separador).build();
            return csvToBean.parse();
        }
    }

    // =========================================================
    //  LÓGICA 2: LEITURA OFICIAL DE NFe (XML DA SEFAZ)
    // =========================================================


    public List<Map<String, Object>> extrairProdutosDoXmlSefaz(MultipartFile file) {
        List<Map<String, Object>> produtosEncontrados = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.getInputStream());
            doc.getDocumentElement().normalize();

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

                produtosEncontrados.add(produto);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao ler o XML da Nota Fiscal: Certifique-se que é uma NF-e válida da SEFAZ.");
        }
        return produtosEncontrados;
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagNameNS("*", tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }


    @Transactional
    public void salvarProdutosLidos(List<Map<String, Object>> produtosLidos) {
        Long empresaId = getEmpresaIdLogada();
        Empresa empresaLogada = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada."));

        for (Map<String, Object> map : produtosLidos) {
            String codigoBarras = map.get("codigoBarras").toString();
            String nome = map.get("nome").toString();
            Integer quantidade = Integer.parseInt(map.get("quantidade").toString());
            // Lê como BigDecimal para não perder cêntimos
            BigDecimal precoCusto = new BigDecimal(map.get("precoCusto").toString());
            String ncm = map.containsKey("ncm") ? map.get("ncm").toString() : null;

            Optional<Produto> produtoExistente = produtoRepository.findByCodigoBarrasAndEmpresaId(codigoBarras, empresaId);

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
                Produto pSalvo = produtoRepository.save(p);
                if (quantidade > 0) criarLoteInicial(pSalvo, quantidade);

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

                Produto pSalvo = produtoRepository.save(pNovo);
                if (quantidade > 0) criarLoteInicial(pSalvo, quantidade);
            }
        }
    }

    private void criarLoteInicial(Produto produto, Integer quantidade) {
        Lote lote = new Lote();
        lote.setProduto(produto);
        lote.setQuantidade(quantidade);
        lote.setDataValidade(LocalDate.now().plusYears(1));
        loteRepository.save(lote);
    }
}