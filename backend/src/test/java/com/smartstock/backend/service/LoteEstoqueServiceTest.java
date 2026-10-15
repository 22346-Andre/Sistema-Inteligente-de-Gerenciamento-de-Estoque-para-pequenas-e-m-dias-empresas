package com.smartstock.backend.service;

import com.smartstock.backend.exception.RegraNegocioException;
import com.smartstock.backend.model.Lote;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.LoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoteEstoqueServiceTest {

    @Mock
    private LoteRepository loteRepository;

    @InjectMocks
    private LoteEstoqueService service;

    @Test
    void baixaQuantidadeDosLotesNaOrdemRecebida() {
        Produto produto = new Produto();
        produto.setId(1L);
        Lote primeiro = lote(4);
        Lote segundo = lote(7);
        when(loteRepository.findLotesDisponiveisParaBaixa(1L)).thenReturn(List.of(primeiro, segundo));

        service.baixar(produto, 6);

        assertEquals(0, primeiro.getQuantidade());
        assertEquals(5, segundo.getQuantidade());
        verify(loteRepository).saveAll(List.of(primeiro, segundo));
    }

    @Test
    void rejeitaBaixaMaiorQueSaldoDosLotes() {
        Produto produto = new Produto();
        produto.setId(1L);
        when(loteRepository.findLotesDisponiveisParaBaixa(1L)).thenReturn(List.of(lote(2)));

        assertThrows(RegraNegocioException.class, () -> service.baixar(produto, 3));
    }

    @Test
    void criaLoteLegadoQuandoProdutoAindaNaoTemLotes() {
        Produto produto = new Produto();
        produto.setId(1L);
        produto.setQuantidade(5);
        when(loteRepository.findLotesDisponiveisParaBaixa(1L)).thenReturn(List.of());

        service.baixar(produto, 2);

        ArgumentCaptor<Lote> loteCaptor = ArgumentCaptor.forClass(Lote.class);
        verify(loteRepository).save(loteCaptor.capture());
        assertEquals("SALDO-LEGADO", loteCaptor.getValue().getNumeroLote());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Lote>> lotesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(loteRepository).saveAll(lotesCaptor.capture());
        assertEquals(3, lotesCaptor.getValue().iterator().next().getQuantidade());
    }

    @Test
    void rejeitaQuantidadeInvalida() {
        Produto produto = new Produto();

        assertThrows(RegraNegocioException.class, () -> service.baixar(produto, 0));
    }

    private Lote lote(int quantidade) {
        Lote lote = new Lote();
        lote.setQuantidade(quantidade);
        return lote;
    }
}
