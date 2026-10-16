package com.kaneko.estacionamento.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MembroOrganizacaoTest {

    @Test
    void nasceAtivoESegueMaquinaDeEstadosAteEncerramentoTerminal() {
        MembroOrganizacao membro = MembroOrganizacao.adicionar(10L, 20L, OrigemMembroOrganizacao.ADMINISTRATIVA);

        assertThat(membro.getOrganizacaoId()).isEqualTo(10L);
        assertThat(membro.getUsuarioId()).isEqualTo(20L);
        assertThat(membro.getStatus()).isEqualTo(StatusMembroOrganizacao.ATIVO);
        assertThat(membro.getEntrouEm()).isNotNull();
        assertThat(membro.getSuspensoEm()).isNull();
        assertThat(membro.getEncerradoEm()).isNull();

        membro.suspender();
        assertThat(membro.getStatus()).isEqualTo(StatusMembroOrganizacao.SUSPENSO);
        assertThat(membro.getSuspensoEm()).isNotNull();

        membro.reativar();
        assertThat(membro.getStatus()).isEqualTo(StatusMembroOrganizacao.ATIVO);
        assertThat(membro.getSuspensoEm()).isNull();

        membro.encerrar();
        assertThat(membro.getStatus()).isEqualTo(StatusMembroOrganizacao.ENCERRADO);
        assertThat(membro.getEncerradoEm()).isNotNull();
        assertThatThrownBy(membro::reativar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void rejeitaTransicoesQueNaoPartemDoEstadoEsperado() {
        MembroOrganizacao membro = MembroOrganizacao.adicionar(10L, 20L, OrigemMembroOrganizacao.ADMINISTRATIVA);

        assertThatThrownBy(membro::reativar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suspensa");
        membro.suspender();
        assertThatThrownBy(membro::suspender)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ativa");
    }
}
