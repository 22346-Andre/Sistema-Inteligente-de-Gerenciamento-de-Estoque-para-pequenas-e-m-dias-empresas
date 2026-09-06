package com.smartstock.backend.service;

import com.smartstock.backend.dto.LancamentoCaixaDTO;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.FormaPagamento;
import com.smartstock.backend.model.MovimentoCaixa;
import com.smartstock.backend.model.OrigemCaixa;
import com.smartstock.backend.model.TipoMovimentoCaixa;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentoCaixaRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CaixaService {

    private final MovimentoCaixaRepository movimentoCaixaRepository;
    private final EmpresaRepository empresaRepository;

    public CaixaService(MovimentoCaixaRepository movimentoCaixaRepository, EmpresaRepository empresaRepository) {
        this.movimentoCaixaRepository = movimentoCaixaRepository;
        this.empresaRepository = empresaRepository;
    }

    // ==== Chamados por outros services quando o dinheiro de fato se move ====
    // Não expostos como endpoint — são acionados de dentro de
    // ProdutoService (venda; e compra de mercadoria à vista via adicionarLote),
    // FiadoService (fiado pago) e DespesaService (despesa paga, incluindo
    // compra de mercadoria a prazo, ver ProdutoService.registrarEfeitoFinanceiroDaCompra).
    // Ver os pontos de chamada em cada um.

    public void registrarEntrada(Empresa empresa, OrigemCaixa origem, BigDecimal valor, String descricao) {
        registrar(empresa, TipoMovimentoCaixa.ENTRADA, origem, valor, descricao, null);
    }

    // 🆕 Overload usado pela venda no PDV — guarda a forma de pagamento pra
    // dar pra saber depois quanto entrou em ESPÉCIE de verdade (necessário
    // pra conferência de gaveta no fechamento de caixa; cartão/PIX contam
    // pro saldo da empresa mas nunca estão na gaveta física).
    public void registrarEntrada(Empresa empresa, OrigemCaixa origem, BigDecimal valor, String descricao, FormaPagamento formaPagamento) {
        registrar(empresa, TipoMovimentoCaixa.ENTRADA, origem, valor, descricao, formaPagamento);
    }

    public void registrarSaida(Empresa empresa, OrigemCaixa origem, BigDecimal valor, String descricao) {
        registrar(empresa, TipoMovimentoCaixa.SAIDA, origem, valor, descricao, null);
    }

    private void registrar(Empresa empresa, TipoMovimentoCaixa tipo, OrigemCaixa origem, BigDecimal valor, String descricao, FormaPagamento formaPagamento) {
        // Valor zero/nulo não é um lançamento de caixa de verdade (ex.: venda
        // com precoVenda e precoCusto ambos nulos) — não polui o extrato com
        // uma linha de R$0,00 sem significado nenhum.
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        MovimentoCaixa movimento = new MovimentoCaixa();
        movimento.setEmpresa(empresa);
        movimento.setTipo(tipo);
        movimento.setOrigem(origem);
        movimento.setValor(valor);
        movimento.setDescricao(descricao);
        movimento.setFormaPagamento(formaPagamento);
        movimento.setUsuarioId(getUsuarioIdLogado());
        movimento.setUsuarioNome(getUsuarioNomeLogado());
        movimentoCaixaRepository.save(movimento);
    }

    // 🆕 Quem está logado agora. Defensivo de propósito: registrar() também é
    // chamado por FiadoService.marcarComoPagoPorCorrelationId, que roda a
    // partir de um webhook de pagamento (rota pública, sem usuário logado) —
    // nesse caso o lançamento simplesmente fica sem usuário atribuído, em vez
    // de derrubar o webhook inteiro.
    private Long getUsuarioIdLogado() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt jwt) {
                return jwt.getClaim("id");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getUsuarioNomeLogado() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt jwt) {
                return jwt.getClaim("nome");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ==== Uso direto pela tela de Caixa ====

    public List<MovimentoCaixa> listarExtrato(Long empresaId) {
        return movimentoCaixaRepository.findByEmpresaIdOrderByDataMovimentoDesc(empresaId);
    }

    public BigDecimal obterSaldoAtual(Long empresaId) {
        return movimentoCaixaRepository.calcularSaldoAtual(empresaId);
    }

    // Lançamento manual — só faz sentido pra aporte/retirada de sócio ou
    // "outro"; os demais tipos de origem são reservados pros lançamentos
    // automáticos, então bloqueia aqui pra não bagunçar a DFC com um "venda
    // PDV" duplicado feito à mão por engano.
    public MovimentoCaixa registrarLancamentoManual(Long empresaId, LancamentoCaixaDTO dto) {
        if (dto.getOrigem() != OrigemCaixa.APORTE_SOCIO && dto.getOrigem() != OrigemCaixa.RETIRADA_SOCIO && dto.getOrigem() != OrigemCaixa.OUTRO) {
            throw new RegraNegocioException("Lançamento manual só pode ser Aporte de Sócio, Retirada de Sócio ou Outro — os demais tipos são preenchidos automaticamente pelo sistema.");
        }
        if (dto.getValor() == null || dto.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Informe um valor maior que zero.");
        }
        if (dto.getTipo() == null) {
            throw new RegraNegocioException("Informe se é uma entrada ou saída.");
        }

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RegraNegocioException("Empresa não encontrada."));

        MovimentoCaixa movimento = new MovimentoCaixa();
        movimento.setEmpresa(empresa);
        movimento.setTipo(dto.getTipo());
        movimento.setOrigem(dto.getOrigem());
        movimento.setValor(dto.getValor());
        movimento.setDescricao(dto.getDescricao());
        movimento.setUsuarioId(getUsuarioIdLogado());
        movimento.setUsuarioNome(getUsuarioNomeLogado());
        return movimentoCaixaRepository.save(movimento);
    }
}
