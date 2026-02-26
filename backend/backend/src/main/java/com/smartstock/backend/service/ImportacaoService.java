package com.smartstock.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Lote;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.LoteRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImportacaoService {

    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;
    private final LoteRepository loteRepository;

    public ImportacaoService(ProdutoRepository produtoRepository, EmpresaRepository empresaRepository, LoteRepository loteRepository) {
        this.produtoRepository = produtoRepository;
        this.empresaRepository = empresaRepository;
        this.loteRepository = loteRepository;
    }

    // --- MÉTODO AUXILIAR PARA PEGAR A EMPRESA DO TOKEN JWT ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    @Transactional // Garante que se der erro, ele desfaz a importação inteira
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

        // 2. LÓGICA MULTI-TENANT: Descobre de quem é o arquivo de verdade
        Long tenantIdAtual = getEmpresaIdLogada();
        Empresa empresaLogada = empresaRepository.findById(tenantIdAtual)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada no contexto de segurança."));

        List<Produto> produtosNovos = new ArrayList<>();
        List<Produto> produtosAtualizados = new ArrayList<>();
        List<String> nomesAdicionados = new ArrayList<>();

        // 3. Processa produto por produto
        for (Produto pLido : produtosLidos) {

            Optional<Produto> produtoExistente = produtoRepository.findByNomeAndEmpresa(pLido.getNome(), empresaLogada);
            Integer quantidadeLida = (pLido.getQuantidade() != null) ? pLido.getQuantidade() : 0;

            if (produtoExistente.isPresent()) {
                // O PRODUTO JÁ EXISTE: Vamos atualizar
                Produto pBase = produtoExistente.get();
                Integer quantidadeAtual = (pBase.getQuantidade() != null) ? pBase.getQuantidade() : 0;

                // Soma a quantidade
                pBase.setQuantidade(quantidadeAtual + quantidadeLida);
                if (pLido.getPreco() != null) pBase.setPreco(pLido.getPreco());

                Produto pSalvo = produtoRepository.save(pBase);
                produtosAtualizados.add(pSalvo);

                // CRIA O LOTE PARA AS UNIDADES NOVAS QUE ENTRARAM
                if (quantidadeLida > 0) {
                    criarLoteInicial(pSalvo, quantidadeLida);
                }

            } else {
                // O PRODUTO NÃO EXISTE: cria
                pLido.setEmpresa(empresaLogada);

                Produto pSalvo = produtoRepository.save(pLido); // Salva para gerar o ID
                produtosNovos.add(pSalvo);
                nomesAdicionados.add(pSalvo.getNome());

                // CRIA O LOTE INICIAL DESTE PRODUTO NOVO
                if (quantidadeLida > 0) {
                    criarLoteInicial(pSalvo, quantidadeLida);
                }
            }
        }

        // 4. Monta o relatório de retorno
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

    // --- MÉTODO AUXILIAR PARA GERAR OS LOTES FEFO ---
    private void criarLoteInicial(Produto produto, Integer quantidade) {
        Lote lote = new Lote();
        lote.setProduto(produto);
        lote.setQuantidade(quantidade);
        // Coloca validade para daqui a 1 ano como padrão de segurança para o sistema não travar
        lote.setDataValidade(LocalDate.now().plusYears(1));
        loteRepository.save(lote);
    }

    private List<Produto> lerCsv(MultipartFile ficheiro) throws Exception {
        try (Reader reader = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            CsvToBean<Produto> csvToBean = new CsvToBeanBuilder<Produto>(reader)
                    .withType(Produto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withSeparator(',')
                    .build();
            return csvToBean.parse();
        }
    }

    private List<Produto> lerXml(MultipartFile ficheiro) throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        return xmlMapper.readValue(ficheiro.getInputStream(), new TypeReference<List<Produto>>() {});
    }
}