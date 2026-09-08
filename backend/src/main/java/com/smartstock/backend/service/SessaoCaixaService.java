package com.smartstock.backend.service;

import com.smartstock.backend.dto.AbrirSessaoCaixaDTO;
import com.smartstock.backend.dto.FecharSessaoCaixaDTO;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.SessaoCaixa;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.MovimentoCaixaRepository;
import com.smartstock.backend.repository.SessaoCaixaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessaoCaixaService {

    @Autowired
    private SessaoCaixaRepository repository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private MovimentoCaixaRepository movimentoCaixaRepository;

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    private Long getUsuarioIdLogado() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("id");
    }

    private String getUsuarioNomeLogado() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("nome");
    }

    // A sessão aberta do usuário logado, se existir (senão null — front usa
    // isso pra decidir se mostra "Abrir Caixa" ou o status "aberto desde...").
    public SessaoCaixa buscarSessaoAtual() {
        return repository.findByEmpresaIdAndUsuarioAberturaIdAndDataFechamentoIsNull(
                getEmpresaIdLogada(), getUsuarioIdLogado()).orElse(null);
    }

    public SessaoCaixa abrir(AbrirSessaoCaixaDTO dto) {
        Long empresaId = getEmpresaIdLogada();
        Long usuarioId = getUsuarioIdLogado();

        boolean jaTemAberta = repository
                .findByEmpresaIdAndUsuarioAberturaIdAndDataFechamentoIsNull(empresaId, usuarioId)
                .isPresent();
        if (jaTemAberta) {
            throw new IllegalStateException("Você já tem um caixa aberto. Feche o atual antes de abrir outro.");
        }

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada."));

        SessaoCaixa sessao = new SessaoCaixa();
        sessao.setEmpresa(empresa);
        sessao.setUsuarioAberturaId(usuarioId);
        sessao.setUsuarioAberturaNome(getUsuarioNomeLogado());
        sessao.setDataAbertura(LocalDateTime.now());
        sessao.setValorAbertura(dto.getValorAbertura());
        sessao.setObservacao(dto.getObservacao());

        return repository.save(sessao);
    }

    public SessaoCaixa fechar(FecharSessaoCaixaDTO dto) {
        Long empresaId = getEmpresaIdLogada();
        Long usuarioId = getUsuarioIdLogado();

        SessaoCaixa sessao = repository
                .findByEmpresaIdAndUsuarioAberturaIdAndDataFechamentoIsNull(empresaId, usuarioId)
                .orElseThrow(() -> new IllegalStateException("Você não tem nenhum caixa aberto pra fechar."));

        LocalDateTime agora = LocalDateTime.now();

        // 🆕 Esperado = fundo de troco declarado na abertura + tudo que esse
        // operador vendeu em ESPÉCIE desde que abriu o caixa até agora.
        BigDecimal vendasEspecie = movimentoCaixaRepository.somarVendasEspecieDoOperadorNoIntervalo(
                empresaId, usuarioId, sessao.getDataAbertura(), agora);
        BigDecimal fundoDeTroco = sessao.getValorAbertura() != null ? sessao.getValorAbertura() : BigDecimal.ZERO;
        sessao.setValorEsperado(fundoDeTroco.add(vendasEspecie));

        sessao.setDataFechamento(agora);
        sessao.setUsuarioFechamentoId(usuarioId);
        sessao.setUsuarioFechamentoNome(getUsuarioNomeLogado());
        sessao.setValorFechamentoInformado(dto.getValorFechamentoInformado());
        if (dto.getObservacao() != null && !dto.getObservacao().isBlank()) {
            sessao.setObservacao(dto.getObservacao());
        }

        return repository.save(sessao);
    }

    // Histórico da empresa toda (todos os operadores) — usado numa tela
    // administrativa, não no PDV do dia a dia.
    public List<SessaoCaixa> listarHistorico() {
        return repository.findByEmpresaIdOrderByDataAberturaDesc(getEmpresaIdLogada());
    }
}
