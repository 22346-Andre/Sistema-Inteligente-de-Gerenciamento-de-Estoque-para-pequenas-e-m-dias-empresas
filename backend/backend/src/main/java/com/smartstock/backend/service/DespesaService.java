package com.smartstock.backend.service;

import com.smartstock.backend.dto.DespesaDTO;
import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.model.Despesa;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.model.OrigemCaixa;
import com.smartstock.backend.model.StatusConta;
import com.smartstock.backend.repository.DespesaRepository;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final EmpresaRepository empresaRepository;
    private final FornecedorRepository fornecedorRepository;
    private final CaixaService caixaService;

    public DespesaService(DespesaRepository despesaRepository, EmpresaRepository empresaRepository,
                           FornecedorRepository fornecedorRepository, CaixaService caixaService) {
        this.despesaRepository = despesaRepository;
        this.empresaRepository = empresaRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.caixaService = caixaService;
    }

    public Despesa registrar(Long empresaId, DespesaDTO dto) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: id=" + empresaId));

        Despesa despesa = new Despesa();
        despesa.setEmpresa(empresa);
        despesa.setDescricao(dto.getDescricao());
        despesa.setCategoria(dto.getCategoria());
        despesa.setValor(dto.getValor());
        despesa.setDataVencimento(dto.getDataVencimento());
        despesa.setStatus(StatusConta.PENDENTE);

        if (dto.getFornecedorId() != null) {
            Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado: id=" + dto.getFornecedorId()));
            despesa.setFornecedor(fornecedor);
        }

        return despesaRepository.save(despesa);
    }

    public List<Despesa> listar(Long empresaId) {
        return despesaRepository.findByEmpresaIdOrderByDataVencimentoAsc(empresaId);
    }

    public List<Despesa> listarEmAberto(Long empresaId) {
        // ATRASADO não é atualizado automaticamente ainda (mesma situação do
        // ContaReceber hoje) — a tela de contas a pagar recalcula visualmente
        // se dataVencimento já passou, sem precisar de job noturno pra isso.
        return despesaRepository.findByEmpresaIdAndStatus(empresaId, StatusConta.PENDENTE);
    }

    public Despesa marcarComoPaga(Long id, Long empresaId) {
        Despesa despesa = buscarDaEmpresa(id, empresaId);
        despesa.setStatus(StatusConta.PAGO);
        despesa.setDataPagamento(LocalDate.now());
        Despesa despesaSalva = despesaRepository.save(despesa);
        caixaService.registrarSaida(despesa.getEmpresa(), OrigemCaixa.PAGAMENTO_DESPESA, despesa.getValor(),
                "Despesa paga: " + despesa.getDescricao());
        return despesaSalva;
    }

    public Despesa atualizar(Long id, DespesaDTO dto, Long empresaId) {
        Despesa despesa = buscarDaEmpresa(id, empresaId);

        despesa.setDescricao(dto.getDescricao());
        despesa.setCategoria(dto.getCategoria());
        despesa.setValor(dto.getValor());

        if (dto.getDataVencimento() != null) {
            despesa.setDataVencimento(dto.getDataVencimento());
        }

        if (dto.getFornecedorId() != null) {
            Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado: id=" + dto.getFornecedorId()));
            despesa.setFornecedor(fornecedor);
        }

        return despesaRepository.save(despesa);
    }

    public void excluir(Long id, Long empresaId) {
        Despesa despesa = buscarDaEmpresa(id, empresaId);
        despesaRepository.delete(despesa);
    }

    private Despesa buscarDaEmpresa(Long id, Long empresaId) {
        Despesa despesa = despesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Despesa não encontrada: id=" + id));

        if (!despesa.getEmpresa().getId().equals(empresaId)) {
            throw new AcessoNegadoException("Operação não permitida para esta empresa!");
        }

        return despesa;
    }
}
