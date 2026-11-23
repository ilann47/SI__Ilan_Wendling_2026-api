package com.kaneko.estacionamento.ordensservico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import org.junit.jupiter.api.Test;

class OrdemServicoTest {

    @Test
    void executaFluxoFelizAteConclusaoTerminal() {
        OrdemServico ordem = ordemComItem();

        ordem.iniciar();
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemServico.EM_EXECUCAO);

        ordem.concluir();
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemServico.CONCLUIDA);
        assertThatThrownBy(() -> ordem.cancelar("tentativa tardia"))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void cancelamentoEhTerminal() {
        OrdemServico ordem = ordemComItem();
        ordem.cancelar("Solicitacao do cliente");

        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemServico.CANCELADA);
        assertThatThrownBy(ordem::iniciar).isInstanceOf(ConflitoException.class);
    }

    private static OrdemServico ordemComItem() {
        OrdemServico ordem = new OrdemServico();
        ordem.setOrganizacaoId(1L);
        ordem.adicionarItem(new ItemOrdemServico());
        return ordem;
    }
}
