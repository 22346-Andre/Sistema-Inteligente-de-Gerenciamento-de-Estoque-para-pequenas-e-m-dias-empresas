package com.smartstock.backend.service;

import com.smartstock.backend.exception.RecursoNaoEncontradoException;

import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import com.smartstock.backend.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import com.smartstock.backend.dto.MovimentacaoPdvDTO;
import com.smartstock.backend.dto.PerdasPorMesDTO;
import com.smartstock.backend.dto.ResumoPerdasDTO;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class MovimentacaoService {

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;


    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private LoteEstoqueService loteEstoqueService;

    // --- MÉTODO AUXILIAR DO JWT ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    // 🆕 Quem está logado agora — pra carimbar em cada Movimentacao quem fez
    // (a que horas já vem de graça, é a dataMovimentacao).
    private Long getUsuarioIdLogado() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("id");
    }

    private String getUsuarioNomeLogado() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("nome");
    }

    public List<Movimentacao> listarTodas() {
        // Retorna só o histórico da empresa logada para montar o Dashboard!
        return movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(getEmpresaIdLogada());
    }

    public List<Movimentacao> listarPorProduto(Long produtoId) {
        return movimentacaoRepository.findByProdutoIdAndEmpresaIdOrderByDataMovimentacaoDesc(
                produtoId, getEmpresaIdLogada());
    }

    public ResumoPerdasDTO obterResumoPerdas(int meses) {
        if (meses < 1 || meses > 24) {
            throw new RegraNegocioException("O período deve estar entre 1 e 24 meses.");
        }

        Long empresaId = getEmpresaIdLogada();
        YearMonth mesAtual = YearMonth.now();
        YearMonth primeiroMes = mesAtual.minusMonths(meses - 1L);
        LocalDateTime inicio = primeiroMes.atDay(1).atStartOfDay();
        LocalDateTime fim = mesAtual.plusMonths(1).atDay(1).atStartOfDay();

        List<Object[]> totaisPorMes = movimentacaoRepository.somarPerdasPorMes(
                empresaId, inicio, fim);

        java.util.Map<String, BigDecimal> totais = new java.util.HashMap<>();
        for (Object[] linha : totaisPorMes) {
            int ano = ((Number) linha[0]).intValue();
            int mes = ((Number) linha[1]).intValue();
            BigDecimal valor = linha[2] == null ? BigDecimal.ZERO : (BigDecimal) linha[2];
            totais.put(YearMonth.of(ano, mes).toString(), valor);
        }

        java.util.List<PerdasPorMesDTO> porMes = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < meses; i++) {
            YearMonth mes = primeiroMes.plusMonths(i);
            BigDecimal valor = totais.getOrDefault(mes.toString(), BigDecimal.ZERO);
            porMes.add(new PerdasPorMesDTO(mes.toString(), valor));
            total = total.add(valor);
        }

        return new ResumoPerdasDTO(total, porMes);
    }

    @Transactional
    public Movimentacao registrarViaPDV(MovimentacaoPdvDTO dto) {
        Long empresaId = getEmpresaIdLogada(); // Usa a sua função segura do JWT
        if (dto == null || dto.getQuantidade() == null || dto.getQuantidade() <= 0) {
            throw new RegraNegocioException("A quantidade deve ser maior que zero.");
        }

        Produto produto = produtoRepository.buscarPorCodigoBarrasComLockParaAtualizacao(dto.getCodigoBarras(), empresaId)
                .orElseThrow(() -> new RuntimeException("Produto com código " + dto.getCodigoBarras() + " não encontrado no seu estoque."));

        TipoMovimentacao tipo;
        try {
            tipo = TipoMovimentacao.valueOf(dto.getTipo().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Tipo de movimentação inválido.");
        }

        if (tipo == TipoMovimentacao.SAIDA) {
            if (produto.getQuantidade() < dto.getQuantidade()) {
                throw new RuntimeException("Estoque insuficiente! Você tentou vender " + dto.getQuantidade() + " mas só tem " + produto.getQuantidade() + " de " + produto.getNome());
            }
            loteEstoqueService.baixar(produto, dto.getQuantidade());
            produto.setQuantidade(produto.getQuantidade() - dto.getQuantidade());
        } else if (tipo == TipoMovimentacao.ENTRADA) {
            loteEstoqueService.registrarEntrada(produto, dto.getQuantidade());
            produto.setQuantidade(produto.getQuantidade() + dto.getQuantidade());
        } else {
            throw new RuntimeException("Tipo de movimentação inválido para o PDV. Use ENTRADA ou SAIDA.");
        }

        // Salva o novo saldo do produto
        produtoRepository.save(produto);

        //  Regista o Histórico da Movimentação
        Movimentacao mov = new Movimentacao();
        mov.setProduto(produto);
        mov.setEmpresa(produto.getEmpresa());
        mov.setTipo(tipo);
        mov.setQuantidade(dto.getQuantidade());
        mov.setDataMovimentacao(java.time.LocalDateTime.now());
        mov.setUsuarioId(getUsuarioIdLogado());
        mov.setUsuarioNome(getUsuarioNomeLogado());

        return movimentacaoRepository.save(mov);
    }
}