package com.kaneko.estacionamento.evento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EventoRepositoryConstraintTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;

    private Long organizacaoId;
    private Long outraOrganizacaoId;
    private Long venueId;

    @BeforeEach
    void setup() {
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Evento Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        outraOrganizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Evento Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        var pais = paises.criar(new PaisRequest(
                "Brasil Evento Constraints", "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest(
                "Parana Evento Constraints", "PR", pais.getId(), true));
        Long cidadeId = cidades.criar(new CidadeRequest(
                "Curitiba Evento Constraints", null, null, estado.getId(), true)).getId();
        venueId = venues.criar(organizacaoId, new CriarVenueRequest(
                "Arena Constraints", "Rua Constraints", null, null, "Centro", "80000-000",
                cidadeId, "America/Sao_Paulo")).id();
    }

    @Test
    void bancoImpedeVenueDeOutroTenant() {
        assertThatThrownBy(() -> inserir(outraOrganizacaoId, venueId, "RASCUNHO",
                OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedePeriodoInvalido() {
        assertThatThrownBy(() -> inserir(organizacaoId, venueId, "RASCUNHO",
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                OffsetDateTime.parse("2026-10-10T19:00:00-03:00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeEstadoInvalido() {
        assertThatThrownBy(() -> inserir(organizacaoId, venueId, "INVENTADO",
                OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(Long orgId, Long localId, String estado,
                         OffsetDateTime inicio, OffsetDateTime fim) {
        jdbc.update("""
                INSERT INTO evento (
                    organizacao_id, venue_id, nome, inicio, fim, fuso_horario, status)
                VALUES (?, ?, 'Evento Constraint', ?, ?, 'America/Sao_Paulo', ?)
                """, orgId, localId, inicio, fim, estado);
    }
}
