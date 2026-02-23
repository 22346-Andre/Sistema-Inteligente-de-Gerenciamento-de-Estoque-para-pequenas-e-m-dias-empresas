package com.smartstock.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImportacaoService {

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;

    public ImportacaoService(ProdutoRepository produtoRepository, EmpresaRepository empresaRepository) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
    }

    public String processarFicheiro(MultipartFile ficheiro) throws Exception {
        String nomeFicheiro = ficheiro.getOriginalFilename();
        List<Produto> produtosLidos;

        // 1. Verifica a extensão e lê o arquivo
        if (nomeFicheiro != null && nomeFicheiro.toLowerCase().endsWith(".csv")) {
            produtosLidos = lerCsv(ficheiro);
        } else if (nomeFicheiro != null && nomeFicheiro.toLowerCase().endsWith(".xml")) {
            produtosLidos = lerXml(ficheiro);
        } else {
            throw new IllegalArgumentException("Formato não suportado. Envie apenas CSV ou XML.");
        }

        /* * =========================================================
         * LÓGICA MULTI-TENANT: VINCULAR A EMPRESA
         * =========================================================
         */
        Long tenantIdAtual = 1L; // Ex: TenantContext.getCurrentTenant();

        Empresa empresaLogada = empresaRepository.findById(tenantIdAtual)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada no contexto de segurança."));

        // 2. Prepara as listas para separar o que é novo do que é atualização
        List<Produto> produtosNovos = new ArrayList<>();
        List<Produto> produtosAtualizados = new ArrayList<>();
        List<String> nomesAdicionados = new ArrayList<>();

        // 3. Verifica produto por produto
        for (Produto pLido : produtosLidos) {

            Optional<Produto> produtoExistente = produtoRepository.findByNomeAndEmpresa(pLido.getNome(), empresaLogada);

            if (produtoExistente.isPresent()) {
                // O PRODUTO JÁ EXISTE: apenas somar o estoque
                Produto pBase = produtoExistente.get();

                Integer quantidadeNova = (pLido.getQuantidade() != null) ? pLido.getQuantidade() : 0;
                Integer quantidadeAtual = (pBase.getQuantidade() != null) ? pBase.getQuantidade() : 0;

                pBase.setQuantidade(quantidadeAtual + quantidadeNova);

                // Atualizar preço também
                if (pLido.getPreco() != null) {
                    pBase.setPreco(pLido.getPreco());
                }

                produtosAtualizados.add(pBase);
            } else {
                // O PRODUTO NÃO EXISTE: Prepara para ser criado
                pLido.setEmpresa(empresaLogada);
                produtosNovos.add(pLido);
                nomesAdicionados.add(pLido.getNome());
            }
        }

        // 4. Salva no banco de dados de forma dividida e otimizada
        if (!produtosAtualizados.isEmpty()) {
            produtoRepository.saveAll(produtosAtualizados);
        }
        if (!produtosNovos.isEmpty()) {
            produtoRepository.saveAll(produtosNovos);
        }

        // 5. Monta o relatório que será exibido no Swagger/Frontend
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

    private List<Produto> lerCsv(MultipartFile ficheiro) throws Exception {
        try (Reader reader = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            CsvToBean<Produto> csvToBean = new CsvToBeanBuilder<Produto>(reader)
                    .withType(Produto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withSeparator(';')
                    .build();

            return csvToBean.parse();
        }
    }

    private List<Produto> lerXml(MultipartFile ficheiro) throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        return xmlMapper.readValue(ficheiro.getInputStream(), new TypeReference<List<Produto>>() {});
    }
}