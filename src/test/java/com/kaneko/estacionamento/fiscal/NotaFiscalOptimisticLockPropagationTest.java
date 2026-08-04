package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class NotaFiscalOptimisticLockPropagationTest {

    @Test
    void entradaPropagaConflitoOtimistaSemGerarTitulos() {
        NotaEntradaRepository notas = mock(NotaEntradaRepository.class);
        ProdutoRepository produtos = mock(ProdutoRepository.class);
        ContaPagarRepository titulos = mock(ContaPagarRepository.class);
        NotaEntradaService service = new NotaEntradaService(notas,
                mock(FornecedorRepository.class), mock(CondicaoPagamentoRepository.class),
                produtos, titulos, mock(LocalEstoqueRepository.class),
                mock(EstoqueService.class), mock(AuditoriaService.class));
        NotaEntrada nota = notaEntradaPendente();
        OptimisticLockingFailureException conflito =
                new OptimisticLockingFailureException("produto concorrente");
        when(notas.findByIdAndOrganizacaoIdIsNull(1L)).thenReturn(Optional.of(nota));
        when(produtos.saveAndFlush(any(Produto.class))).thenThrow(conflito);

        assertThatThrownBy(() -> service.confirmar(null, null, 1L)).isSameAs(conflito);
        verifyNoInteractions(titulos);
    }

    @Test
    void saidaPropagaConflitoOtimistaSemGerarTitulos() {
        NotaSaidaRepository notas = mock(NotaSaidaRepository.class);
        ProdutoRepository produtos = mock(ProdutoRepository.class);
        ContaReceberRepository titulos = mock(ContaReceberRepository.class);
        NotaSaidaService service = new NotaSaidaService(notas,
                mock(ClienteRepository.class), mock(CondicaoPagamentoRepository.class),
                produtos, titulos);
        NotaSaida nota = notaSaidaPendente();
        OptimisticLockingFailureException conflito =
                new OptimisticLockingFailureException("produto concorrente");
        when(notas.findById(1L)).thenReturn(Optional.of(nota));
        when(produtos.saveAndFlush(any(Produto.class))).thenThrow(conflito);

        assertThatThrownBy(() -> service.confirmar(1L)).isSameAs(conflito);
        verifyNoInteractions(titulos);
    }

    private NotaEntrada notaEntradaPendente() {
        Produto produto = produtoHistorico();
        ItemNotaEntrada item = new ItemNotaEntrada();
        item.setProduto(produto);
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(BigDecimal.ONE);
        item.setCustoPrecoFinal(BigDecimal.ZERO);
        NotaEntrada nota = new NotaEntrada();
        nota.setSituacao(SituacaoNota.PENDENTE);
        nota.addItem(item);
        return nota;
    }

    private NotaSaida notaSaidaPendente() {
        Produto produto = produtoHistorico();
        ItemNotaSaida item = new ItemNotaSaida();
        item.setProduto(produto);
        item.setQuantidade(BigDecimal.ONE);
        NotaSaida nota = new NotaSaida();
        nota.setSituacao(SituacaoNota.PENDENTE);
        nota.addItem(item);
        return nota;
    }

    private Produto produtoHistorico() {
        Produto produto = new Produto();
        produto.setNome("Produto historico");
        produto.setQuantidade(new BigDecimal("100"));
        return produto;
    }
}
