package com.kaneko.estacionamento.organizacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrganizacaoTest {

    @Test
    void provisionamentoConcluiComOrganizacaoAtivaENormalizada() {
        Organizacao organizacao = Organizacao.provisionar(
                "12.345.678/0001-95",
                "Kaneko Estacionamentos Ltda",
                "Kaneko",
                "brl",
                "America/Sao_Paulo",
                "br-pr",
                "enterprise");

        assertThat(organizacao.getDocumento()).isEqualTo("12345678000195");
        assertThat(organizacao.getMoeda()).isEqualTo("BRL");
        assertThat(organizacao.getRegiao()).isEqualTo("BR-PR");
        assertThat(organizacao.getPlano()).isEqualTo("ENTERPRISE");
        assertThat(organizacao.getStatus()).isEqualTo(StatusOrganizacao.ATIVA);
    }

    @Test
    void organizacaoEncerradaEhTerminalENaoPodeSerAtualizada() {
        Organizacao organizacao = novaOrganizacao();

        organizacao.encerrar();

        assertThat(organizacao.getStatus()).isEqualTo(StatusOrganizacao.ENCERRADA);
        assertThatThrownBy(() -> organizacao.atualizarCadastro(
                "Nova Razao", "Novo Nome", "USD", "America/New_York", "US-NY", "ENTERPRISE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encerrada");
        assertThatThrownBy(organizacao::reativar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void rejeitaMoedaFusoRegiaoEPlanoInvalidos() {
        assertThatThrownBy(() -> Organizacao.provisionar(
                "12.345.678/0001-95", "Kaneko", null, "REAL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("moeda");
        assertThatThrownBy(() -> Organizacao.provisionar(
                "12.345.678/0001-95", "Kaneko", null, "BRL", "Sao_Paulo", "BR-PR", "ENTERPRISE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fuso");
        assertThatThrownBy(() -> Organizacao.provisionar(
                "12.345.678/0001-95", "Kaneko", null, "BRL", "America/Sao_Paulo", "sul", "ENTERPRISE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regiao");
        assertThatThrownBy(() -> Organizacao.provisionar(
                "12.345.678/0001-95", "Kaneko", null, "BRL", "America/Sao_Paulo", "BR-PR", "plano livre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plano");
    }

    private static Organizacao novaOrganizacao() {
        return Organizacao.provisionar(
                "12.345.678/0001-95", "Kaneko", "Kaneko", "BRL",
                "America/Sao_Paulo", "BR-PR", "ENTERPRISE");
    }
}
