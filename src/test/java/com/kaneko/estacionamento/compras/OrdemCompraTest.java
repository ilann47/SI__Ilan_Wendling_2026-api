package com.kaneko.estacionamento.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrdemCompraTest {

    @Test
    void percorreCicloDeAprovacaoERecebimento() {
        OrdemCompra ordem = ordemComItem("5");

        ordem.aprovar();
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemCompra.APROVADA);

        ordem.getItens().getFirst().receber(new BigDecimal("2"));
        ordem.atualizarStatusRecebimento();
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemCompra.PARCIALMENTE_RECEBIDA);

        ordem.getItens().getFirst().receber(new BigDecimal("3"));
        ordem.atualizarStatusRecebimento();
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemCompra.RECEBIDA);
    }

    @Test
    void impedeExcessoECancelamentoAposPrimeiroRecebimento() {
        OrdemCompra ordem = ordemComItem("5");
        ordem.aprovar();

        assertThatThrownBy(() -> ordem.getItens().getFirst().receber(new BigDecimal("6")))
                .isInstanceOf(ConflitoException.class);

        ordem.getItens().getFirst().receber(BigDecimal.ONE);
        ordem.atualizarStatusRecebimento();
        assertThatThrownBy(() -> ordem.cancelar("Compra desnecessaria"))
                .isInstanceOf(ConflitoException.class);
    }

    private OrdemCompra ordemComItem(String quantidade) {
        OrdemCompra ordem = new OrdemCompra();
        ordem.setOrganizacaoId(1L);
        ItemOrdemCompra item = new ItemOrdemCompra();
        item.setQuantidadePedida(new BigDecimal(quantidade));
        item.setQuantidadeRecebida(BigDecimal.ZERO);
        ordem.adicionarItem(item);
        return ordem;
    }
}
