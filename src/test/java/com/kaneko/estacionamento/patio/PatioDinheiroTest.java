package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PatioDinheiroTest {
    @Test void cancelamentoLegadoSemOrdemNemPendenciaNaoMostraValorPago() {
        var antiga=new PatioEventoRepository.Estadia(1,2,3,"Evento","AAA1A23",PatioEventoService.Modo.ENTRADA,
            new BigDecimal("25"),PatioCaixaService.Meio.DINHEIRO,false,null,java.time.OffsetDateTime.now(),
            null,null,null,StatusMovimentacao.CANCELADO);
        assertThat(PatioEventoService.detalhe(antiga).valorPago()).isEqualByComparingTo("0");
    }
    @Test void conservaSeisCasasESomenteDinheiroAceitaRecebido() {
        var valor = new BigDecimal("25.123456");
        assertThat(PatioDinheiro.conferir(PatioCaixaService.Meio.DINHEIRO, valor, new BigDecimal("30")).troco())
                .isEqualByComparingTo("4.876544");
        assertThat(PatioDinheiro.conferir(PatioCaixaService.Meio.DINHEIRO, valor, null).recebido())
                .isEqualByComparingTo(valor);
        assertThatThrownBy(() -> PatioDinheiro.conferir(PatioCaixaService.Meio.DINHEIRO, valor, new BigDecimal("25")))
                .hasMessageContaining("inferior");
        assertThatThrownBy(() -> PatioDinheiro.conferir(PatioCaixaService.Meio.PIX, valor, new BigDecimal("30")))
                .hasMessageContaining("dinheiro");
        assertThatThrownBy(() -> PatioDinheiro.conferir(PatioCaixaService.Meio.DINHEIRO, valor, new BigDecimal("30.0000001")))
                .hasMessageContaining("seis");
    }
}
