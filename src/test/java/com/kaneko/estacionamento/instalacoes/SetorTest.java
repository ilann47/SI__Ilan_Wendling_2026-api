package com.kaneko.estacionamento.instalacoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SetorTest {

    @Test
    void aceitaConjuntoImutavelDeCategoriasValidas() {
        Setor setor = Setor.criar(
                1L,
                2L,
                "A",
                "Setor A",
                100,
                Set.of(CategoriaSetor.COMUM, CategoriaSetor.PCD));

        assertThat(setor.categorias())
                .containsExactly(CategoriaSetor.COMUM, CategoriaSetor.PCD);
    }
}
