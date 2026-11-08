package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import org.junit.jupiter.api.Test;

class NotaSaidaDetalhePermissoesTest {
    @Test
    void detalheFiscalNaoConsultaEstoqueFinanceiroNemParcelasSemPermissao() {
        var notas = mock(NotaSaidaService.class);
        var contas = mock(ContaReceberRepository.class);
        var movimentos = mock(MovimentoEstoqueRepository.class);
        var nota = new NotaSaida();
        nota.setCliente(new Cliente());
        nota.getParcelas().add(new ParcelaNotaSaida());
        when(notas.buscar(1L, 2L)).thenReturn(nota);
        var detalhe = new NotaSaidaDetalheService(notas, contas, movimentos)
                .buscar(1L, 2L, false, false);
        assertThat(detalhe.nota()).isNotNull();
        assertThat(detalhe.parcelas()).isNull();
        assertThat(detalhe.contasReceber()).isNull();
        assertThat(detalhe.movimentosEstoque()).isNull();
        verifyNoInteractions(contas, movimentos);
    }
}
