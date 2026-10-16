package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.compras.OrdemCompra;
import com.kaneko.estacionamento.compras.OrdemCompraRepository;
import com.kaneko.estacionamento.compras.RecebimentoCompraRepository;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompraDocumentoServiceTest {
    private final OrdemCompraRepository ordens = mock(OrdemCompraRepository.class);
    private final RecebimentoCompraRepository recebimentos = mock(RecebimentoCompraRepository.class);
    private final NotaEntradaRepository notas = mock(NotaEntradaRepository.class);
    private final ContaPagarRepository contas = mock(ContaPagarRepository.class);
    private final MovimentoEstoqueRepository movimentos = mock(MovimentoEstoqueRepository.class);
    private final CompraDocumentoService service = new CompraDocumentoService(ordens, recebimentos, notas, contas, movimentos);

    @Test
    void naoConsultaSecoesSemPermissao() {
        when(ordens.findByIdAndOrganizacaoId(2L, 1L)).thenReturn(Optional.of(new OrdemCompra()));
        when(recebimentos.findAllByOrganizacaoIdAndOrdemCompraIdOrderByRecebidoEmDesc(1L, 2L)).thenReturn(List.of());
        var result = service.buscar(1L, 2L, false, false, false);
        assertThat(result.notas()).isNull();
        assertThat(result.contasPagar()).isNull();
        assertThat(result.movimentosEstoque()).isNull();
        verifyNoInteractions(notas, contas, movimentos);
    }

    @Test
    void validaOwnerAntesDeConsultarDocumentos() {
        when(ordens.findByIdAndOrganizacaoId(2L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buscar(1L, 2L, true, true, true))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(recebimentos, notas, contas, movimentos);
    }
}
