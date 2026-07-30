package com.kaneko.estacionamento.organizacao.venue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class VenueRepositoryConstraintTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;

    private Long organizacaoId;
    private Long cidadeId;

    @BeforeEach
    void setup() {
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Venue Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        var pais = paises.criar(new PaisRequest(
                "Brasil Venue Constraints", "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest(
                "Parana Venue Constraints", "PR", pais.getId(), true));
        cidadeId = cidades.criar(new CidadeRequest(
                "Curitiba Venue Constraints", null, null, estado.getId(), true)).getId();
    }

    @Test
    void bancoImpedeNomeNormalizadoDuplicadoNoTenant() {
        inserir("Arena Constraint", "VENUE");

        assertThatThrownBy(() -> inserir("  arena constraint  ", "VENUE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeTipoNaoAprovado() {
        assertThatThrownBy(() -> inserir("Unidade Sem Contrato", "FILIAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(String nome, String tipo) {
        jdbc.update("""
                INSERT INTO unidade_operacional (
                    organizacao_id, nome, tipo, endereco, bairro, cep,
                    cidade_id, fuso_horario, ativo)
                VALUES (?, ?, ?, 'Rua Constraint', 'Centro', '80000-000',
                        ?, 'America/Sao_Paulo', true)
                """, organizacaoId, nome, tipo, cidadeId);
    }
}
