package com.kaneko.estacionamento.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MilionesimosConverterTest {
    @Test
    void preservaAsSeisCasasEInteirosGrandesSemArredondar() {
        var converter = new MilionesimosConverter();
        for (String valor : new String[] {"120.000001", "1.125001", "5.250001", "-0.000001", "99999999999999999.999999"}) {
            var decimal = new BigDecimal(valor);
            var gravado = converter.convertToDatabaseColumn(decimal);
            assertThat(gravado).isEqualTo(decimal.movePointRight(6).setScale(0));
            assertThat(converter.convertToEntityAttribute(gravado)).isEqualTo(decimal);
        }
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> converter.convertToDatabaseColumn(new BigDecimal("0.0000001")))
                .isInstanceOf(ArithmeticException.class);
    }
}
