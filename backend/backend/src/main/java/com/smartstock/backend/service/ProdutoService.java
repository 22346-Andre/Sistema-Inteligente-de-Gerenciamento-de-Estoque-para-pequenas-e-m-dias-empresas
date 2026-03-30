package com.smartstock.backend.service;

import com.smartstock.backend.dto.LoteDTO;
import com.smartstock.backend.dto.ProdutoDTO;
import com.smartstock.backend.model.*;
import com.smartstock.backend.repository.*;
import com.smartstock.backend.specification.ProdutoSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository repository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private LoteRepository loteRepository;

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }


    private String calcularCfopInterno(TipoMovimentacao tipo, Produto produto) {
        if (tipo == TipoMovimentacao.QUEBRA_PERDA) {
            return "5.927"; // Baixa por perecimento/avaria
        }

        // Verifica se o produto tem Substituição Tributária (ST) no seu CFOP padrão
        boolean temST = produto.getCfop() != null && produto.getCfop().contains("405");

        // Assumindo operações internas (Maranhão) como padrão para o MVP
        if (tipo == TipoMovimentacao.ENTRADA) {
            return temST ? "1.403" : "1.102"; // Compras
        }

        if (tipo == TipoMovimentacao.SAIDA) {
            return temST ? "5.405" : "5.102"; // Vendas
        }

        return "0.000";
    }

    public List<Produto> listarTodos() {
        Long empresaId = getEmpresaIdLogada();
        List<Produto> produtos = repository.findByEmpresaId(empresaId);
        BigDecimal totalEstoque = repository.calcularValorTotalEstoque(empresaId);

        if (totalEstoque == null || totalEstoque.compareTo(BigDecimal.ZERO) == 0) {
            return produtos;
        }

        produtos.sort((p1, p2) -> {
            BigDecimal v1 = (p1.getPrecoCusto() != null ? p1.getPrecoCusto() : BigDecimal.ZERO)
                    .multiply(new BigDecimal(p1.getQuantidade() != null ? p1.getQuantidade() : 0));
            BigDecimal v2 = (p2.getPrecoCusto() != null ? p2.getPrecoCusto() : BigDecimal.ZERO)
                    .multiply(new BigDecimal(p2.getQuantidade() != null ? p2.getQuantidade() : 0));
            return v2.compareTo(v1);
        });

        BigDecimal acumulado = BigDecimal.ZERO;
        for (Produto p : produtos) {
            BigDecimal valorItem = (p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO)
                    .multiply(new BigDecimal(p.getQuantidade() != null ? p.getQuantidade() : 0));

            acumulado = acumulado.add(valorItem);
            double percentual = acumulado.divide(totalEstoque, 4, RoundingMode.HALF_UP).doubleValue() * 100;

            if (percentual <= 80.0) {
                p.setClassificacaoABC("A");
            } else if (percentual <= 95.0) {
                p.setClassificacaoABC("B");
            } else {
                p.setClassificacaoABC("C");
            }
        }

        produtos.sort(Comparator.comparing(Produto::getNome));
        return produtos;
    }

    public Produto buscarPorId(Long id) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Este produto pertence a outra empresa.");
        }

        return produto;
    }

    public List<Produto> listarEstoqueCritico() {
        return repository.findProdutosComEstoqueBaixoPorEmpresa(getEmpresaIdLogada());
    }

    public Produto salvar(ProdutoDTO dto) {
        if (repository.existsByNome(dto.getNome())) {
            throw new RuntimeException("Erro: O produto '" + dto.getNome() + "' já existe no sistema!");
        }

        Long empresaIdLogada = getEmpresaIdLogada();
        Empresa empresa = empresaRepository.findById(empresaIdLogada)
                .orElseThrow(() -> new RuntimeException("Erro: Empresa não encontrada."));

        Produto produto = new Produto();
        produto.setNome(dto.getNome());
        produto.setCodigoBarras(dto.getCodigoBarras());
        produto.setCategoria(dto.getCategoria());
        produto.setPrecoCusto(dto.getPrecoCusto());
        produto.setPrecoVenda(dto.getPrecoVenda());
        produto.setEstoqueMinimo(dto.getQuantidadeMinima() != null ? dto.getQuantidadeMinima() : 5);
        produto.setQuantidade(dto.getQuantidade() != null ? dto.getQuantidade() : 0);
        produto.setDescricao(dto.getDescricao());
        produto.setNcm(dto.getNcm());
        produto.setCfop(dto.getCfop());

        if (dto.getImpostos() != null) {
            produto.setImpostos(dto.getImpostos());
        }

        produto.setFinalidadeEstoque(dto.getFinalidadeEstoque() != null ? dto.getFinalidadeEstoque().toUpperCase() : "REVENDA");

        produto.setUnidade(dto.getUnidade() != null ? dto.getUnidade().toUpperCase() : "UN");
        produto.setEmpresa(empresa);

        if (dto.getFornecedorId() != null) {
            Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                    .orElseThrow(() -> new RuntimeException("Erro: Fornecedor ID " + dto.getFornecedorId() + " não encontrado!"));
            produto.setFornecedor(fornecedor);
        }

        return repository.save(produto);
    }

    public Produto atualizar(Long id, ProdutoDTO dto) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode alterar um produto de outra empresa.");
        }

        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setCodigoBarras(dto.getCodigoBarras());
        produto.setCategoria(dto.getCategoria());
        produto.setPrecoCusto(dto.getPrecoCusto());
        produto.setPrecoVenda(dto.getPrecoVenda());
        produto.setEstoqueMinimo(dto.getQuantidadeMinima() != null ? dto.getQuantidadeMinima() : 5);
        produto.setQuantidade(dto.getQuantidade());
        produto.setNcm(dto.getNcm());
        produto.setCfop(dto.getCfop());

        if (dto.getImpostos() != null) {
            produto.setImpostos(dto.getImpostos());
        } else {
            produto.getImpostos().clear();
        }

        produto.setFinalidadeEstoque(dto.getFinalidadeEstoque() != null ? dto.getFinalidadeEstoque().toUpperCase() : "REVENDA");
        produto.setUnidade(dto.getUnidade() != null ? dto.getUnidade().toUpperCase() : "UN");

        if (dto.getFornecedorId() != null) {
            Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado com ID: " + dto.getFornecedorId()));

            if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
                throw new RuntimeException("Acesso negado: Este fornecedor pertence a outra empresa.");
            }
            produto.setFornecedor(fornecedor);
        } else {
            produto.setFornecedor(null);
        }

        return repository.save(produto);
    }

    public void deletar(Long id) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode deletar um produto de outra empresa.");
        }

        repository.deleteById(id);
    }

    public List<Produto> buscaAvancada(String categoria, BigDecimal precoMin, BigDecimal precoMax, LocalDateTime dataInicio) {
        Long empresaId = getEmpresaIdLogada();
        Specification<Produto> spec = ProdutoSpecification.pertenceAEmpresa(empresaId);

        if (categoria != null && !categoria.isBlank()) {
            spec = spec.and(ProdutoSpecification.categoriaContem(categoria));
        }
        if (precoMin != null || precoMax != null) {
            spec = spec.and(ProdutoSpecification.precoEntre(precoMin, precoMax));
        }
        if (dataInicio != null) {
            spec = spec.and(ProdutoSpecification.atualizadoApos(dataInicio));
        }

        return repository.findAll(spec);
    }

    @jakarta.transaction.Transactional
    public Movimentacao registrarSaida(Long produtoId, Integer quantidadeDesejada, TipoMovimentacao tipo, String motivo, String chaveNotaFiscal) { // 🟢 Adicionamos o 5º parâmetro aqui

        Produto produto = repository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode dar baixa em um produto de outra empresa.");
        }

        if (produto.getQuantidade() < quantidadeDesejada) {
            throw new RuntimeException("Estoque insuficiente! Saldo atual: " + produto.getQuantidade());
        }

        List<Lote> lotes = loteRepository.findLotesDisponiveisParaBaixa(produtoId);
        int quantidadeRestante = quantidadeDesejada;

        for (Lote lote : lotes) {
            if (quantidadeRestante == 0) break;

            if (lote.getQuantidade() <= quantidadeRestante) {
                quantidadeRestante -= lote.getQuantidade();
                lote.setQuantidade(0);
            } else {
                lote.setQuantidade(lote.getQuantidade() - quantidadeRestante);
                quantidadeRestante = 0;
            }
            loteRepository.save(lote);
        }

        produto.setQuantidade(produto.getQuantidade() - quantidadeDesejada);
        Produto produtoAtualizado = repository.save(produto);

        // CÁLCULO DO CFOP AUTOMÁTICO
        TipoMovimentacao tipoFinal = tipo != null ? tipo : TipoMovimentacao.SAIDA;
        String cfopOperacao = calcularCfopInterno(tipoFinal, produto);

        Movimentacao mov = new Movimentacao();
        mov.setProduto(produtoAtualizado);
        mov.setTipo(tipoFinal);

        String motivoFinal = (motivo != null && !motivo.isBlank()) ? motivo : "Operação registrada";
        mov.setMotivo("[CFOP " + cfopOperacao + "] " + motivoFinal);

        mov.setQuantidade(quantidadeDesejada);
        mov.setEmpresa(produtoAtualizado.getEmpresa());


        mov.setChaveNotaFiscal(chaveNotaFiscal);

        return movimentacaoRepository.save(mov);
    }

    @jakarta.transaction.Transactional
    public Produto adicionarLote(Long produtoId, LoteDTO dto, BigDecimal novoPrecoCompra) {

        Produto produto = repository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Não pode alterar o estoque de outra empresa.");
        }

        int quantidadeAtual = produto.getQuantidade() != null ? produto.getQuantidade() : 0;
        BigDecimal precoCustoAtual = produto.getPrecoCusto() != null ? produto.getPrecoCusto() : BigDecimal.ZERO;
        int novaQuantidade = dto.getQuantidade();

        if (novoPrecoCompra != null && novoPrecoCompra.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal valorEstoqueAtual = precoCustoAtual.multiply(new BigDecimal(quantidadeAtual));
            BigDecimal valorNovaCompra = novoPrecoCompra.multiply(new BigDecimal(novaQuantidade));

            BigDecimal somaValores = valorEstoqueAtual.add(valorNovaCompra);
            int totalItens = quantidadeAtual + novaQuantidade;

            BigDecimal custoMedio = somaValores.divide(new BigDecimal(totalItens), 2, RoundingMode.HALF_UP);
            produto.setPrecoCusto(custoMedio);
        }

        Lote novoLote = new Lote();
        novoLote.setNumeroLote(dto.getNumeroLote());
        novoLote.setQuantidade(dto.getQuantidade());
        novoLote.setDataValidade(dto.getDataValidade());
        novoLote.setProduto(produto);
        loteRepository.save(novoLote);

        produto.setQuantidade(quantidadeAtual + novaQuantidade);
        Produto produtoAtualizado = repository.save(produto);

        // CÁLCULO DO CFOP DE ENTRADA
        String cfopOperacao = calcularCfopInterno(TipoMovimentacao.ENTRADA, produto);

        Movimentacao mov = new Movimentacao();
        mov.setProduto(produtoAtualizado);
        mov.setTipo(TipoMovimentacao.ENTRADA);
        mov.setQuantidade(novaQuantidade);
        mov.setEmpresa(produtoAtualizado.getEmpresa());

        String obs = "[CFOP " + cfopOperacao + "] Entrada de lote";
        if (dto.getNumeroLote() != null && !dto.getNumeroLote().isEmpty()) {
            obs += " (" + dto.getNumeroLote() + ")";
        }
        mov.setMotivo(obs);

        movimentacaoRepository.save(mov);

        return produtoAtualizado;
    }
}