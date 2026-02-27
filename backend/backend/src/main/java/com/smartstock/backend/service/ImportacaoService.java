package com.smartstock.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
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

    @Transactional
    public String processarFicheiro(MultipartFile ficheiro) throws Exception {
        String nomeFicheiro = ficheiro.getOriginalFilename();
        List<ProdutoDTO> produtosLidos;

        if (nomeFicheiro != null && nomeFicheiro.toLowerCase().endsWith(".csv")) {
            produtosLidos = lerCsv(ficheiro);
        } else if (nomeFicheiro != null && nomeFicheiro.toLowerCase().endsWith(".xml")) {
            produtosLidos = lerXml(ficheiro);
        } else {
            throw new IllegalArgumentException("Formato não suportado. Envie apenas CSV ou XML.");
        }

        Long tenantIdAtual = getEmpresaIdLogada();
        Empresa empresaLogada = empresaRepository.findById(tenantIdAtual)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada no contexto de segurança."));

        List<Produto> produtosNovos = new ArrayList<>();
        List<Produto> produtosAtualizados = new ArrayList<>();
        List<String> nomesAdicionados = new ArrayList<>();

        for (ProdutoDTO pLido : produtosLidos) {

            // Ignora se a linha estiver vazia por acidente
            if (pLido.getNome() == null || pLido.getNome().trim().isEmpty()) {
                continue;
            }

            Optional<Produto> produtoExistente = produtoRepository.findByNomeAndEmpresa(pLido.getNome(), empresaLogada);
            Integer quantidadeLida = (pLido.getQuantidade() != null) ? pLido.getQuantidade() : 0;

            if (produtoExistente.isPresent()) {
                Produto pBase = produtoExistente.get();
                Integer quantidadeAtual = (pBase.getQuantidade() != null) ? pBase.getQuantidade() : 0;

                pBase.setQuantidade(quantidadeAtual + quantidadeLida);
                if (pLido.getPreco() != null) pBase.setPreco(pLido.getPreco());

                // Se o CSV vier com fornecedor, ele atualiza
                if (pLido.getFornecedorId() != null) {
                    fornecedorRepository.findById(pLido.getFornecedorId())
                            .ifPresent(pBase::setFornecedor);
                }

                Produto pSalvo = produtoRepository.save(pBase);
                produtosAtualizados.add(pSalvo);

                if (quantidadeLida > 0) {
                    criarLoteInicial(pSalvo, quantidadeLida);
                }

            } else {
                Produto pNovo = new Produto();
                pNovo.setNome(pLido.getNome());
                pNovo.setDescricao(pLido.getDescricao());
                pNovo.setPreco(pLido.getPreco());
                pNovo.setQuantidade(pLido.getQuantidade());
                pNovo.setEstoqueMinimo(pLido.getEstoqueMinimo() != null ? pLido.getEstoqueMinimo() : 5);
                pNovo.setNcm(pLido.getNcm());
                pNovo.setUnidade(pLido.getUnidade());
                pNovo.setEmpresa(empresaLogada);

                // Casamento inicial com o Fornecedor
                if (pLido.getFornecedorId() != null) {
                    fornecedorRepository.findById(pLido.getFornecedorId())
                            .ifPresent(pNovo::setFornecedor);
                }

                Produto pSalvo = produtoRepository.save(pNovo);
                produtosNovos.add(pSalvo);
                nomesAdicionados.add(pSalvo.getNome());

                if (quantidadeLida > 0) {
                    criarLoteInicial(pSalvo, quantidadeLida);
                }
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

    private void criarLoteInicial(Produto produto, Integer quantidade) {
        Lote lote = new Lote();
        lote.setProduto(produto);
        lote.setQuantidade(quantidade);
        lote.setDataValidade(LocalDate.now().plusYears(1));
        loteRepository.save(lote);
    }

    private List<ProdutoDTO> lerCsv(MultipartFile ficheiro) throws Exception {
        List<String> linhasLimpas = new ArrayList<>();
        char separador = ',';

        try (BufferedReader br = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            boolean primeiraLinha = true;
            int numColunasCabecalho = 0;

            while ((linha = br.readLine()) != null) {
                // 1. Pula qualquer linha que esteja 100% em branco
                if (linha.trim().isEmpty()) continue;

                if (primeiraLinha) {
                    //  Separador
                    if (linha.contains(";")) {
                        separador = ';';
                    }
                    // Conta quantas colunas o cabeçalho exige
                    numColunasCabecalho = linha.split(String.valueOf(separador), -1).length;
                    linhasLimpas.add(linha);
                    primeiraLinha = false;
                } else {
                    // 2. Filtro de Impurezas: Só aceita a linha se ela tiver O MESMO número de colunas do cabeçalho
                    if (linha.split(String.valueOf(separador), -1).length == numColunasCabecalho) {
                        linhasLimpas.add(linha);
                    }
                }
            }
        }

        // 3. Junta as linhas perfeitas num único texto
        String csvLimpo = String.join("\n", linhasLimpas);

        // 4. Entrega o texto cirurgicamente limpo para o OpenCSV
        try (Reader reader = new java.io.StringReader(csvLimpo)) {
            CsvToBean<ProdutoDTO> csvToBean = new CsvToBeanBuilder<ProdutoDTO>(reader)
                    .withType(ProdutoDTO.class)
                    .withSeparator(separador)
                    .build();

            return csvToBean.parse();
        }
    }

    private List<ProdutoDTO> lerXml(MultipartFile ficheiro) throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        return xmlMapper.readValue(ficheiro.getInputStream(), new TypeReference<List<ProdutoDTO>>() {});
    }

    @Transactional
    public String importarCsvFornecedores(MultipartFile ficheiro) throws Exception {
        // 1.  Separador para Fornecedores
        char separador = ',';
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            String primeiraLinha = br.readLine();
            if (primeiraLinha != null && primeiraLinha.contains(";")) {
                separador = ';';
            }
        }

        // 2. Lê os Fornecedores do CSV
        List<com.smartstock.backend.dto.FornecedorDTO> fornecedoresLidos;
        try (Reader reader = new BufferedReader(new InputStreamReader(ficheiro.getInputStream(), StandardCharsets.UTF_8))) {
            CsvToBean<com.smartstock.backend.dto.FornecedorDTO> csvToBean = new CsvToBeanBuilder<com.smartstock.backend.dto.FornecedorDTO>(reader)
                    .withType(com.smartstock.backend.dto.FornecedorDTO.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .withSeparator(separador)
                    .build();
            fornecedoresLidos = csvToBean.parse();
        }

        Empresa empresaLogada = empresaRepository.findById(getEmpresaIdLogada())
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada."));

        int salvos = 0;

        // 3. Salva no banco de dados
        for (com.smartstock.backend.dto.FornecedorDTO fDto : fornecedoresLidos) {
            if (fDto.getNome() == null || fDto.getNome().trim().isEmpty()) continue;

            Fornecedor fNovo = new Fornecedor();
            fNovo.setNome(fDto.getNome());
            fNovo.setCnpj(fDto.getCnpj());
            fNovo.setEmail(fDto.getEmail());
            fNovo.setTelefone(fDto.getTelefone());
            fNovo.setEndereco(fDto.getEndereco());
            fNovo.setEmpresa(empresaLogada);

            fornecedorRepository.save(fNovo);
            salvos++;
        }

        return "Importação concluída! Fornecedores cadastrados: " + salvos;
    }
}