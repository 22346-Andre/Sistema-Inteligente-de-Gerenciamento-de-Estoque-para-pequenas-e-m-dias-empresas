package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Lote;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.LoteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class LoteEstoqueService {

    private final LoteRepository loteRepository;

    public LoteEstoqueService(LoteRepository loteRepository) {
        this.loteRepository = loteRepository;
    }

    public void registrarEntrada(Produto produto, int quantidade) {
        registrarEntrada(produto, quantidade, "AJUSTE", null);
    }

    public void registrarEntrada(Produto produto, int quantidade, String numeroLote, LocalDate dataValidade) {
        if (quantidade <= 0) {
            throw new RegraNegocioException("A quantidade de entrada deve ser maior que zero.");
        }

        Lote lote = new Lote();
        lote.setProduto(produto);
        lote.setNumeroLote(numeroLote);
        lote.setQuantidade(quantidade);
        lote.setDataValidade(dataValidade);
        loteRepository.save(lote);
    }

    public void baixar(Produto produto, int quantidade) {
        if (quantidade <= 0) {
            throw new RegraNegocioException("A quantidade de saída deve ser maior que zero.");
        }

        List<Lote> lotes = loteRepository.findLotesDisponiveisParaBaixa(produto.getId());

        // Produtos antigos podem ter saldo sem lotes registrados. Preserve o
        // saldo existente em um lote sem validade antes de começar a controlá-lo.
        if (lotes.isEmpty() && produto.getQuantidade() != null && produto.getQuantidade() > 0) {
            Lote loteLegado = new Lote();
            loteLegado.setProduto(produto);
            loteLegado.setNumeroLote("SALDO-LEGADO");
            loteLegado.setQuantidade(produto.getQuantidade());
            loteRepository.save(loteLegado);
            lotes = List.of(loteLegado);
        }

        int quantidadeDisponivel = lotes.stream()
                .mapToInt(lote -> lote.getQuantidade() == null ? 0 : lote.getQuantidade())
                .sum();
        if (quantidadeDisponivel < quantidade) {
            throw new RegraNegocioException(
                    "O saldo dos lotes é inferior ao estoque do produto. Reconcilie os lotes antes de continuar.");
        }

        int restante = quantidade;
        for (Lote lote : lotes) {
            if (restante == 0) {
                break;
            }

            int saldoLote = lote.getQuantidade() == null ? 0 : lote.getQuantidade();
            int abatimento = Math.min(saldoLote, restante);
            lote.setQuantidade(saldoLote - abatimento);
            restante -= abatimento;
        }
        loteRepository.saveAll(lotes);
    }
}
