package com.smartstock.backend.service;

import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;

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
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
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

    
    public org.springframework.data.domain.Page<Produto> listarPaginado(int page, int size, String busca, String categoria) {
        Long empresaId = getEmpresaIdLogada();

        org.springframework.data.jpa.domain.Specification<Produto> spec =
                ProdutoSpecification.pertenceAEmpresa(empresaId);

        if (busca != null && !busca.isBlank()) {
            spec = spec.and(ProdutoSpecification.nomeOuCodigoBarrasContem(busca.trim()));
        }

       
        if (categoria != null && !categoria.isBlank()) {
            spec = spec.and(ProdutoSpecification.categoriaContem(categoria.trim()));
        }

        int pageSeguro = Math.max(0, page);
        int sizeSeguro = Math.min(Math.max(1, size), 200); // trava contra ?size=999999

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageSeguro, sizeSeguro,
                org.springframework.data.domain.Sort.by("nome").ascending()
        );

        return repository.findAll(spec, pageable);
    }

    
    public java.util.List<String> listarCategoriasDistintas() {
        Long empresaId = getEmpresaIdLogada();
        return repository.findCategoriasDistintasPorEmpresa(empresaId);
    }
    // listarTodos() — inclusive do Scanner/PDV e do Dashboard, que chamam isso a
    // cada carregamento de tela — recalculava a Curva ABC inteira do zero, pelo
    // critério ERRADO (capital parado em estoque, ver CurvaAbcService). Além de
    // caro (sort + 2 passadas sobre todo o catálogo em toda requisição), a
    // classificação não tinha nem uso real nessa tela. Curva ABC agora vive só
    // em GET /estatisticas/curva-abc, calculada sob demanda quando o gestor
    // realmente abre esse painel — não em toda listagem de produto do sistema.
    public List<Produto> listarTodos() {
        Long empresaId = getEmpresaIdLogada();
        return repository.findByEmpresaId(empresaId);
    }

    /** Valor total do produto parado em estoque: custo unitário × quantidade. Usado na Curva ABC. */
    private BigDecimal valorEmEstoque(Produto p) {
        return (p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO)
                .multiply(new BigDecimal(p.getQuantidade() != null ? p.getQuantidade() : 0));
    }

    
    @jakarta.transaction.Transactional
    public Produto buscarPorId(Long id) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com o ID: " + id));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Este produto pertence a outra empresa.");
        }

        produto.getImpostos().size(); // força o carregamento agora, dentro da transação

        return produto;
    }

    public List<Produto> listarEstoqueCritico() {
        return repository.findProdutosComEstoqueBaixoPorEmpresa(getEmpresaIdLogada());
    }

    @jakarta.transaction.Transactional
    public Produto salvar(ProdutoDTO dto) {
        Long empresaIdLogada = getEmpresaIdLogada();
        Empresa empresa = empresaRepository.findById(empresaIdLogada)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Erro: Empresa não encontrada."));

        // A unicidade do nome deve valer só dentro da própria empresa, não no
        // sistema inteiro — senão a Empresa A cadastrar "Arroz" impediria
        // qualquer outra empresa de usar esse mesmo nome.
        if (repository.findByNomeAndEmpresa(dto.getNome(), empresa).isPresent()) {
            throw new RuntimeException("Erro: O produto '" + dto.getNome() + "' já existe no sistema!");
        }

        Produto produto = new Produto();
        produto.setNome(dto.getNome());
        produto.setCodigoBarras(dto.getCodigoBarras());
        produto.setCategoria(dto.getCategoria());
        produto.setPrecoCusto(dto.getPrecoCusto());
        produto.setPrecoVenda(dto.getPrecoVenda());
        produto.setEstoqueMinimo(dto.getQuantidadeMinima() != null ? dto.getQuantidadeMinima() : 5);

        Integer quantidadeInicial = dto.getQuantidade() != null ? dto.getQuantidade() : 0;
        produto.setQuantidade(quantidadeInicial);

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

        Produto produtoSalvo = repository.save(produto);

        
        if (quantidadeInicial > 0) {
            String cfopOperacao = calcularCfopInterno(TipoMovimentacao.ENTRADA, produtoSalvo);

            Movimentacao movInicial = new Movimentacao();
            movInicial.setProduto(produtoSalvo);
            movInicial.setTipo(TipoMovimentacao.ENTRADA);
            movInicial.setQuantidade(quantidadeInicial);
            movInicial.setEmpresa(empresa);
            movInicial.setMotivo("[CFOP " + cfopOperacao + "] Estoque inicial de cadastro");
            // Setado explicitamente (não depende do valor padrão do campo na
            // entidade) — garante que as consultas por dataMovimentacao
            // (Curva ABC, Giro de Estoque) enxerguem essa movimentação.
            movInicial.setDataMovimentacao(java.time.LocalDateTime.now());

            movimentacaoRepository.save(movInicial);
        }

        return produtoSalvo;
    }

    @jakarta.transaction.Transactional
    public Produto atualizar(Long id, ProdutoDTO dto) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com o ID: " + id));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Você não pode alterar um produto de outra empresa.");
        }

        Integer quantidadeAntes = produto.getQuantidade() != null ? produto.getQuantidade() : 0;

        produto.setNome(dto.getNome());
        produto.setDescricao(dto.getDescricao());
        produto.setCodigoBarras(dto.getCodigoBarras());
        produto.setCategoria(dto.getCategoria());
        produto.setPrecoCusto(dto.getPrecoCusto());
        produto.setPrecoVenda(dto.getPrecoVenda());
        produto.setEstoqueMinimo(dto.getQuantidadeMinima() != null ? dto.getQuantidadeMinima() : 5);
        // Se o payload não enviar quantidade, mantém a quantidade atual em vez de
        // gravar null (o que quebraria com NPE qualquer comparação numérica
        // posterior, como em MovimentacaoService.registrarViaPDV/registrarSaida).
        produto.setQuantidade(dto.getQuantidade() != null ? dto.getQuantidade() : quantidadeAntes);
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
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado com ID: " + dto.getFornecedorId()));

            if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
                throw new AcessoNegadoException("Acesso negado: Este fornecedor pertence a outra empresa.");
            }
            produto.setFornecedor(fornecedor);
        } else {
            produto.setFornecedor(null);
        }

        Produto produtoAtualizado = repository.save(produto);

        // Se a edição mudou a quantidade em estoque, registra a diferença como
        // movimentação (ENTRADA ou SAIDA), para manter o rastro completo usado
        // pelos relatórios retroativos (Balanço Geral / Inventário Fiscal).
        Integer quantidadeDepois = dto.getQuantidade() != null ? dto.getQuantidade() : quantidadeAntes;
        int diferenca = quantidadeDepois - quantidadeAntes;

        if (diferenca != 0) {
            Movimentacao ajuste = new Movimentacao();
            ajuste.setProduto(produtoAtualizado);
            ajuste.setEmpresa(produtoAtualizado.getEmpresa());
            ajuste.setMotivo("Ajuste manual via edição de produto");

            if (diferenca > 0) {
                ajuste.setTipo(TipoMovimentacao.ENTRADA);
                ajuste.setQuantidade(diferenca);
            } else {
                ajuste.setTipo(TipoMovimentacao.SAIDA);
                ajuste.setQuantidade(Math.abs(diferenca));
            }
            // Setado explicitamente — mesmo motivo do comentário acima.
            ajuste.setDataMovimentacao(java.time.LocalDateTime.now());

            movimentacaoRepository.save(ajuste);
        }

        return produtoAtualizado;
    }

    public void deletar(Long id) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com o ID: " + id));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Você não pode deletar um produto de outra empresa.");
        }

        
        if (movimentacaoRepository.existsByProdutoId(id)) {
            throw new RuntimeException(
                "Não é possível excluir este produto porque ele já possui movimentações de estoque registradas. " +
                "Se ele não deve mais aparecer no seu catálogo, considere zerar a quantidade em vez de excluí-lo."
            );
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
    public Movimentacao registrarSaida(Long produtoId, Integer quantidadeDesejada, TipoMovimentacao tipo, String motivo, String chaveNotaFiscal, FormaPagamento formaPagamento) { // 🟢 Adicionamos o 6º parâmetro aqui
        return registrarSaidaInterno(produtoId, quantidadeDesejada, tipo, motivo, chaveNotaFiscal, formaPagamento, getEmpresaIdLogada());
    }

    //  — bug crítico: o Webhook (rota pública, sem JWT — ver
    // SecurityConfigurations, /api/webhooks/** é permitAll) chamava
    // registrarSaida() diretamente, que por baixo dos panos sempre chama
    // getEmpresaIdLogada(). Sem usuário autenticado, SecurityContextHolder traz
    // um Authentication anônimo cujo getPrincipal() é a STRING "anonymousUser",
    // não um Jwt — o cast (Jwt) explodia com ClassCastException em TODA venda
    // vinda de canal externo, sempre, silenciosamente engolida pelo catch
    // genérico do WebhookService. Ou seja, nenhuma baixa de estoque via
    // Webhook jamais funcionou de verdade.
    //
    // Esta variante recebe a empresa já validada por fora (pelo segredo do
    // Webhook, nunca por JWT) e não depende do contexto de segurança —
    // usada exclusivamente pelo WebhookService.
    @jakarta.transaction.Transactional
    public Movimentacao registrarSaidaComEmpresa(Long produtoId, Integer quantidadeDesejada, TipoMovimentacao tipo,
                                                  String motivo, String chaveNotaFiscal, FormaPagamento formaPagamento, Long empresaId) {
        return registrarSaidaInterno(produtoId, quantidadeDesejada, tipo, motivo, chaveNotaFiscal, formaPagamento, empresaId);
    }

    private Movimentacao registrarSaidaInterno(Long produtoId, Integer quantidadeDesejada, TipoMovimentacao tipo,
                                                String motivo, String chaveNotaFiscal, FormaPagamento formaPagamento, Long empresaIdDono) {

        // Lock pessimista: trava essa linha de produto até o fim da transação, pra
        // duas baixas de estoque simultâneas no MESMO produto não pisarem uma na
        // outra (lost update). A segunda requisição fica esperando a primeira
        // terminar, em vez de ler uma quantidade já desatualizada.
        Produto produto = repository.buscarComLockParaAtualizacao(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado"));

        if (!produto.getEmpresa().getId().equals(empresaIdDono)) {
            throw new AcessoNegadoException("Acesso negado: Você não pode dar baixa em um produto de outra empresa.");
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

        // Setado explicitamente — é essa data que as consultas de Curva ABC,
        // Giro de Estoque e sugestão de compra usam pra filtrar "últimos N
        // dias"; depender só do valor padrão do campo na entidade deixa essa
        // consulta cega a qualquer atraso entre construir e persistir o objeto.
        mov.setDataMovimentacao(java.time.LocalDateTime.now());

        mov.setChaveNotaFiscal(chaveNotaFiscal);
        mov.setFormaPagamento(formaPagamento); // 

        return movimentacaoRepository.save(mov);
    }

    @jakarta.transaction.Transactional
    public Produto adicionarLote(Long produtoId, LoteDTO dto, BigDecimal novoPrecoCompra) {

        Produto produto = repository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado"));

        if (!produto.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Não pode alterar o estoque de outra empresa.");
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
        mov.setDataMovimentacao(java.time.LocalDateTime.now());

        String obs = "[CFOP " + cfopOperacao + "] Entrada de lote";
        if (dto.getNumeroLote() != null && !dto.getNumeroLote().isEmpty()) {
            obs += " (" + dto.getNumeroLote() + ")";
        }
        mov.setMotivo(obs);

        movimentacaoRepository.save(mov);

        return produtoAtualizado;
    }
}