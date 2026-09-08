package com.smartstock.backend.service;

import com.smartstock.backend.dto.FornecedorDTO;
import com.smartstock.backend.exception.AcessoNegadoException;
import com.smartstock.backend.exception.RecursoNaoEncontradoException;
import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.FornecedorRepository;
import com.smartstock.backend.repository.MovimentacaoRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FornecedorService {

    private final FornecedorRepository repository;
    private final EmpresaRepository empresaRepository;
    private final ProdutoRepository produtoRepository; 
    private final MovimentacaoRepository movimentacaoRepository;

    // Injeção de dependência via construtor (Clean Code: dependências explícitas e imutáveis)
    public FornecedorService(FornecedorRepository repository,
                             EmpresaRepository empresaRepository,
                             ProdutoRepository produtoRepository,
                             MovimentacaoRepository movimentacaoRepository) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    // prazo de entrega REAL, calculado a partir do intervalo médio
    // entre ENTRADAS consecutivas de produtos desse fornecedor — em vez do
    // valor fixo digitado uma vez no cadastro e nunca mais atualizado.
    //
    // Exige pelo menos 3 entradas registradas (2 intervalos) pra confiar no
    // dado observado; com menos histórico que isso, a amostra é pequena
    // demais e devolve null — quem chamar deve cair de volta pro valor
    // configurado manualmente (Fornecedor.prazoEntregaDias) ou no padrão do
    // sistema. Isso é decisão de negócio, não trava do fuzzy: o motor de
    // urgência continua exatamente como está, só passa a receber um dado de
    // prazo mais fiel à realidade quando houver histórico suficiente.
    public Double calcularPrazoEntregaObservado(Long fornecedorId, Long empresaId) {
        List<LocalDateTime> datas = movimentacaoRepository.findDatasEntradaPorFornecedor(fornecedorId, empresaId);

        if (datas.size() < 3) {
            return null;
        }

        long somaDias = 0;
        int intervalos = 0;
        for (int i = 1; i < datas.size(); i++) {
            long dias = ChronoUnit.DAYS.between(datas.get(i - 1), datas.get(i));
            if (dias > 0) { // ignora entradas no mesmo dia (não é um "ciclo de entrega")
                somaDias += dias;
                intervalos++;
            }
        }

        if (intervalos == 0) {
            return null;
        }

        double media = (double) somaDias / intervalos;

        // Trava de sanidade: um intervalo absurdamente longo (ex.: fornecedor
        // com uma entrada há 8 meses e outra ontem, no meio de uma amostra
        // pequena) não deveria dominar o cálculo fuzzy sozinho. Limita à faixa
        // que as funções de pertinência de prazo já cobrem (até "demorado").
        return Math.max(1.0, Math.min(media, 60.0));
    }

    // --- MÉTODO AUXILIAR DA CONFIANÇA ZERO (JWT) ---
    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        if (empresaId == null) {
            throw new RecursoNaoEncontradoException("Erro: O usuário logado não possui vínculo com nenhuma empresa.");
        }
        return empresaId;
    }

    public List<Fornecedor> listarTodos() {
        return repository.findByEmpresaId(getEmpresaIdLogada());
    }

    public Fornecedor salvar(FornecedorDTO dto) {
        Long empresaId = getEmpresaIdLogada();

        if (repository.findByCnpjAndEmpresaId(dto.getCnpj(), empresaId).isPresent()) {
            throw new RegraNegocioException("Você já possui um fornecedor cadastrado com este CNPJ!");
        }

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());
        fornecedor.setPrazoEntregaDias(dto.getPrazoEntregaDias());
        fornecedor.setCategoriasFornecidas(dto.getCategoriasFornecidas());
        fornecedor.setEmpresa(empresa);

        return repository.save(fornecedor);
    }

    public Fornecedor atualizar(Long id, FornecedorDTO dto) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado"));

        if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Este fornecedor pertence a outra empresa.");
        }

        repository.findByCnpjAndEmpresaId(dto.getCnpj(), getEmpresaIdLogada())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new RegraNegocioException("Já existe outro fornecedor com este CNPJ.");
                    }
                });

        fornecedor.setNome(dto.getNome());
        fornecedor.setCnpj(dto.getCnpj());
        fornecedor.setTelefone(dto.getTelefone());
        fornecedor.setEmail(dto.getEmail());
        fornecedor.setEndereco(dto.getEndereco());
        fornecedor.setPrazoEntregaDias(dto.getPrazoEntregaDias());
        fornecedor.setCategoriasFornecidas(dto.getCategoriasFornecidas());

        return repository.save(fornecedor);
    }

    public void deletar(Long id) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado"));

        // TRAVA DE SEGURANÇA SAAS
        if (!fornecedor.getEmpresa().getId().equals(getEmpresaIdLogada())) {
            throw new AcessoNegadoException("Acesso negado: Você não pode deletar um fornecedor de outra empresa.");
        }

        // Validação de negócio ANTES de tocar no banco para evitar erro de Foreign Key
        if (produtoRepository.existsByFornecedorId(id)) {
            throw new RegraNegocioException(
                "Não é possível excluir este fornecedor porque ele está vinculado a produtos do seu estoque. " +
                "Remova o vínculo desses produtos ou cadastre outro fornecedor para eles antes de excluir."
            );
        }

        repository.deleteById(id);
    }
}