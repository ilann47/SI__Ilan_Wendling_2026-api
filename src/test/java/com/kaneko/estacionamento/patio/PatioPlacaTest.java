package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.CampoInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatioPlacaTest {
    @Test
    void aceitaTextoLivreSemModificarOConteudoInterno() {
        assertThat(PatioPlaca.obrigatoria(" hbf593 ")).isEqualTo("HBF593");
        assertThat(PatioPlaca.obrigatoria(" x %_- 7 ")).isEqualTo("X %_- 7");
        assertThat(PatioPlaca.obrigatoria("1234567890")).isEqualTo("1234567890");
        assertThat(PatioPlaca.obrigatoria("🚙")).isEqualTo("🚙");
        assertThat(PatioPlaca.grafiaLegada("X")).isEmpty();
        assertThat(PatioPlaca.grafiaLegada("AB-C1234")).isEmpty();
    }

    @Test
    void preservaCompatibilidadeDaPlacaBrasileiraCompleta() {
        assertThat(PatioPlaca.obrigatoria("abc-1d23")).isEqualTo("ABC1D23");
        assertThat(PatioPlaca.obrigatoria("abc-1234")).isEqualTo("ABC1234");
        assertThat(PatioPlaca.obrigatoria("ab-c1234")).isEqualTo("AB-C1234");
        assertThat(PatioPlaca.grafiaLegada("ABC1D23")).contains("ABC-1D23");
    }

    @Test
    void validaPresencaCaracteresVisiveisELimiteAposMaiusculas() {
        for (var placa : new String[]{null, "", " \u00A0 ", "a\nç", "a\u202E", "12345678901", "123456789ß"}) {
            assertThatThrownBy(() -> PatioPlaca.obrigatoria(placa))
                    .isInstanceOf(CampoInvalidoException.class);
        }
        assertThat(PatioPlaca.normalizar(null)).isEmpty();
    }
}
