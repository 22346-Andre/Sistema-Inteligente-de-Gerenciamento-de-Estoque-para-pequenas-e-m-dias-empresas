package com.smartstock.backend.service;

import com.smartstock.backend.dto.MovimentacaoDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Movimentacao;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.model.TipoMovimentacao;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MovimentacaoService {

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    // --- MÉTODO AUXILIAR DO JWT ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");

        if (empresaId == null) {
            throw new RuntimeException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Movimentacao> listarTodas() {
        //  Retorna só o histórico da empresa logada
        return movimentacaoRepository.findByEmpresaIdOrderByDataMovimentacaoDesc(getEmpresaIdLogada());
    }

    public Movimentacao registrarMovimentacao(MovimentacaoDTO dto) {
        Produto produto = produtoRepository.findById(dto.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + dto.getProdutoId()));

        Long empresaIdLogada = getEmpresaIdLogada();

        // TRAVA DE SEGURANÇA SAAS:
        if (!produto.getEmpresa().getId().equals(empresaIdLogada)) {
            throw new RuntimeException("Acesso negado: Este produto pertence a outra empresa.");
        }

        Empresa empresa = empresaRepository.findById(empresaIdLogada)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada!"));

        // Lógica de Entrada e Saída
        if (dto.getTipo() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidade(produto.getQuantidade() + dto.getQuantidade());
        } else if (dto.getTipo() == TipoMovimentacao.SAIDA) {
            if (produto.getQuantidade() < dto.getQuantidade()) {
                throw new RuntimeException("Estoque insuficiente para a saída solicitada.");
            }
            produto.setQuantidade(produto.getQuantidade() - dto.getQuantidade());
        }

        produtoRepository.save(produto);

        // Registra a movimentação carimbada com a empresa
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setProduto(produto);
        movimentacao.setTipo(dto.getTipo());
        movimentacao.setQuantidade(dto.getQuantidade());
        movimentacao.setDataMovimentacao(LocalDateTime.now());
        movimentacao.setEmpresa(empresa);

        return movimentacaoRepository.save(movimentacao);
    }
}