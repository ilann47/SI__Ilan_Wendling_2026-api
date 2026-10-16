package com.kaneko.estacionamento.inventario.alocacao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.evento.EventoService;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.instalacoes.InstalacoesService;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
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
class AlocacaoPatioEventoConstraintTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;
    @Autowired InstalacoesService instalacoes;
    @Autowired EventoService eventos;
    @Autowired UsuarioService usuarios;

    private Long organizacaoId;
    private Long outraOrganizacaoId;
    private Long eventoId;
    private Long patioId;

    @BeforeEach
    void setup() {
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Allocation Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        outraOrganizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Allocation Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        Long atorId = usuarios.criar(new UsuarioRequest(
                "Ator Constraints", "ator_alloc_constraints", "senha123",
                null, null, PerfilUsuario.ADMIN)).getId();
        Long cidadeId = criarCidade();
        Long venueId = venues.criar(organizacaoId, new CriarVenueRequest(
                "Arena Allocation Constraints", "Rua Arena", null, null, "Centro",
                "80000-000", cidadeId, "America/Sao_Paulo")).id();
        eventoId = eventos.criar(organizacaoId, atorId, "evento-constraint", new CriarEventoRequest(
                "Evento Constraint", venueId,
                OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                "America/Sao_Paulo", null)).id();
        patioId = criarPatio(organizacaoId, cidadeId, "Patio Constraint");
    }

    @Test
    void bancoImpedePatioDeOutroTenant() {
        assertThatThrownBy(() -> inserir(outraOrganizacaoId, eventoId, patioId, 80, 60, 20))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeHierarquiaInvalida() {
        assertThatThrownBy(() -> inserir(organizacaoId, eventoId, patioId, 80, 70, 20))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeDuplicidadeEventoPatio() {
        inserir(organizacaoId, eventoId, patioId, 80, 60, 20);
        assertThatThrownBy(() -> inserir(organizacaoId, eventoId, patioId, 70, 50, 20))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(Long orgId, Long eventId, Long parkingId,
                         int operacional, int vendavel, int reservada) {
        jdbc.update("""
                INSERT INTO alocacao_patio_evento (
                    organizacao_id, evento_id, patio_id, inicio, fim,
                    capacidade_operacional, capacidade_vendavel, capacidade_reservada)
                VALUES (?, ?, ?, '2026-10-10T20:00:00Z', '2026-10-11T04:00:00Z', ?, ?, ?)
                """, orgId, eventId, parkingId, operacional, vendavel, reservada);
    }

    private Long criarCidade() {
        var pais = paises.criar(new PaisRequest(
                "Brasil Allocation Constraints", "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest(
                "Parana Allocation Constraints", "PR", pais.getId(), true));
        return cidades.criar(new CidadeRequest(
                "Curitiba Allocation Constraints", null, null, estado.getId(), true)).getId();
    }

    private Long criarPatio(Long orgId, Long cidadeId, String nome) {
        return instalacoes.criar(orgId, new CriarPatioInstalacaoRequest(
                nome, "Rua Patio", null, null, "Centro", "80000-000", cidadeId,
                100, 100, null, true)).id();
    }
}
