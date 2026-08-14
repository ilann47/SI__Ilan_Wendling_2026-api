package com.kaneko.estacionamento.vendas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import org.junit.jupiter.api.Test;

class VendaAdministrativaTest {
    @Test
    void confirmadaECanceladaSaoTerminais() {
        VendaAdministrativa confirmada = new VendaAdministrativa();
        confirmada.adicionarItem(new ItemVendaAdministrativa());
        confirmada.confirmar();
        assertThat(confirmada.getStatus()).isEqualTo(StatusVendaAdministrativa.CONFIRMADA);
        assertThatThrownBy(() -> confirmada.cancelar("erro")).isInstanceOf(ConflitoException.class);

        VendaAdministrativa cancelada = new VendaAdministrativa();
        cancelada.cancelar("cliente desistiu");
        assertThat(cancelada.getStatus()).isEqualTo(StatusVendaAdministrativa.CANCELADA);
        assertThatThrownBy(cancelada::confirmar).isInstanceOf(ConflitoException.class);
    }
}
