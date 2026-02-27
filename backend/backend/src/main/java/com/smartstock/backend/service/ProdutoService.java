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
import java.time.LocalDateTime;
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

    // --- MÉTODO AUXILIAR PARA NÃO REPETIR CÓDIGO (DRY) ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Produto> listarTodos() {
        return repository.findByEmpresaId(getEmpresaIdLogada());
    }

    public List<Produto> listarEstoqueCritico() {
        return repository.findProdutosComEstoqueBaixoPorEmpresa(getEmpresaIdLogada());
    }

    public Produto salvar(ProdutoDTO dto) {
        // 1. Validação de duplicidade
        if (repository.existsByNome(dto.getNome())) {
            throw new RuntimeException("Erro: O produto '" + dto.getNome() + "' já existe no sistema!");
        }

        Long empresaIdLogada = getEmpresaIdLogada();
        Empresa empresa = empresaRepository.findById(empresaIdLogada)
                .orElseThrow(() -> new RuntimeException("Erro: Empresa não encontrada."));

        Produto produto = new Produto();
        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setQuantidade(dto.getQuantidade());
        produto.setEstoqueMinimo(dto.getEstoqueMinimo() != null ? dto.getEstoqueMinimo() : 5);
        produto.setNcm(dto.getNcm());
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
        // 1. Busca o produto ou estoura erro se não existir
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        // 2. Trava de segurança SaaS: Verifica se o produto pertence à empresa do usuário logado
        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Você não pode alterar um produto de outra empresa.");
        }

        // 3. Atualiza os dados básicos
        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setPreco(dto.getPreco());
        produto.setQuantidade(dto.getQuantidade()); // Adicionado: Sem isso a quantidade não muda no PUT
        produto.setEstoqueMinimo(dto.getEstoqueMinimo());
        produto.setNcm(dto.getNcm());
        produto.setUnidade(dto.getUnidade() != null ? dto.getUnidade().toUpperCase() : "UN");

        // 4. O CASAMENTO: Vincula o Fornecedor ao Produto
        if (dto.getFornecedorId() != null) {
            Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado com ID: " + dto.getFornecedorId()));

            // Garante que o fornecedor também pertença à mesma empresa (Segurança extra)
            if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
                throw new RuntimeException("Acesso negado: Este fornecedor pertence a outra empresa.");
            }

            produto.setFornecedor(fornecedor);
        } else {
            // Se o DTO vier sem fornecedorId, limpamos o vínculo
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

    // --- ALGORITMO FEFO DE BAIXA AUTOMÁTICA + HISTÓRICO ---
    @jakarta.transaction.Transactional
    public void registrarSaida(Long produtoId, Integer quantidadeDesejada) {

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

        // --- GERA O DIÁRIO DE BORDO AUTOMATICAMENTE (SAÍDA) ---
        Movimentacao mov = new Movimentacao();
        mov.setProduto(produtoAtualizado);
        mov.setTipo(TipoMovimentacao.SAIDA);
        mov.setQuantidade(quantidadeDesejada);
        mov.setEmpresa(produtoAtualizado.getEmpresa());
        movimentacaoRepository.save(mov);
    }

    // --- DAR ENTRADA NUM NOVO LOTE + HISTÓRICO ---
    @jakarta.transaction.Transactional
    public Produto adicionarLote(Long produtoId, LoteDTO dto) {

        Produto produto = repository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new RuntimeException("Acesso negado: Não pode alterar o estoque de outra empresa.");
        }

        Lote novoLote = new Lote();
        novoLote.setQuantidade(dto.getQuantidade());
        novoLote.setDataValidade(dto.getDataValidade());
        novoLote.setProduto(produto);
        loteRepository.save(novoLote);

        int quantidadeAtual = produto.getQuantidade() != null ? produto.getQuantidade() : 0;
        produto.setQuantidade(quantidadeAtual + dto.getQuantidade());
        Produto produtoAtualizado = repository.save(produto);

        // --- GERA O DIÁRIO DE BORDO AUTOMATICAMENTE (ENTRADA) ---
        Movimentacao mov = new Movimentacao();
        mov.setProduto(produtoAtualizado);
        mov.setTipo(TipoMovimentacao.ENTRADA);
        mov.setQuantidade(dto.getQuantidade());
        mov.setEmpresa(produtoAtualizado.getEmpresa());
        movimentacaoRepository.save(mov);

        return produtoAtualizado;
    }
}