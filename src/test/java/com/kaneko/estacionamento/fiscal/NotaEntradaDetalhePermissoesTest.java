package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.kaneko.estacionamento.compras.RecebimentoCompraRepository;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import org.junit.jupiter.api.Test;

class NotaEntradaDetalhePermissoesTest {
    @Test
    void leitorFiscalNaoRecebeDadosFinanceirosOuRazaoSemPermissao() {
        var notas = mock(NotaEntradaService.class);
        var contas = mock(ContaPagarRepository.class);
        var recebimentos = mock(RecebimentoCompraRepository.class);
        var movimentos = mock(MovimentoEstoqueRepository.class);
        NotaEntrada nota = new NotaEntrada();
        nota.setFornecedor(new Fornecedor());
        when(notas.buscar(1L, 2L)).thenReturn(nota);
        var detail = new NotaEntradaDetalheService(notas, contas, recebimentos, movimentos)
                .buscar(1L, 2L, false, false, false);
        assertThat(detail.nota()).isNotNull();
        assertThat(detail.contasPagar()).isNull();
        assertThat(detail.movimentosEstoque()).isNull();
        verifyNoInteractions(contas, movimentos);
    }
}
