package com.kaneko.estacionamento.inventario.produto;

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
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoService;
import com.kaneko.estacionamento.inventario.alocacao.dto.CriarAlocacaoPatioEventoRequest;
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
class ProdutoEstacionamentoConstraintTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;
    @Autowired InstalacoesService instalacoes;
    @Autowired EventoService eventos;
    @Autowired AlocacaoPatioEventoService alocacoes;
    @Autowired UsuarioService usuarios;

    private Long organizacaoId;
    private Long eventoId;
    private Long alocacaoId;

    @BeforeEach
    void setup() {
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Product Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        Long atorId = usuarios.criar(new UsuarioRequest(
                "Ator Product Constraints", "ator_product_constraints", "senha123",
                null, null, PerfilUsuario.ADMIN)).getId();
        Long cidadeId = criarCidade();
        Long venueId = venues.criar(organizacaoId, new CriarVenueRequest(
                "Arena Product Constraints", "Rua Arena", null, null, "Centro",
                "80000-000", cidadeId, "America/Sao_Paulo")).id();
        eventoId = eventos.criar(organizacaoId, atorId, "event-product-constraint",
                new CriarEventoRequest("Evento Product Constraint", venueId,
                        OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                        "America/Sao_Paulo", null)).id();
        Long patioId = instalacoes.criar(organizacaoId, new CriarPatioInstalacaoRequest(
                "Patio Product Constraint", "Rua Patio", null, null, "Centro", "80000-000",
                cidadeId, 100, 100, null, true)).id();
        alocacaoId = alocacoes.criar(organizacaoId, atorId, eventoId, "alloc-product-constraint",
                new CriarAlocacaoPatioEventoRequest(
                        patioId, OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                        100, 80, 20)).id();
    }

    @Test
    void bancoImpedeRelacionamentoForaDoTenantOuEvento() {
        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Product Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();

        assertThatThrownBy(() -> inserir(
                outraOrganizacao, eventoId, alocacaoId, "Produto Cruzado", 10, "RASCUNHO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeQuotaInvalida() {
        assertThatThrownBy(() -> inserir(
                organizacaoId, eventoId, alocacaoId, "Quota Invalida", 0, "RASCUNHO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeEstadoInvalido() {
        assertThatThrownBy(() -> inserir(
                organizacaoId, eventoId, alocacaoId, "Estado Invalido", 10, "INVALIDO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeCategoriaInvalida() {
        assertThatThrownBy(() -> jdbc.update(sqlInsercao().replace("'COMUM'", "'INVALIDA'"),
                        organizacaoId, eventoId, alocacaoId, "Categoria Invalida", 10, "RASCUNHO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeNomeNormalizadoDuplicadoNoEvento() {
        inserir(organizacaoId, eventoId, alocacaoId, "Produto VIP", 10, "RASCUNHO");
        assertThatThrownBy(() -> inserir(
                organizacaoId, eventoId, alocacaoId, " produto vip ", 10, "RASCUNHO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(Long orgId, Long eventId, Long allocationId,
                         String nome, int quota, String status) {
        jdbc.update(sqlInsercao(), orgId, eventId, allocationId, nome, quota, status);
    }

    private static String sqlInsercao() {
        return """
                INSERT INTO produto_estacionamento (
                    organizacao_id, evento_id, alocacao_id, nome, categoria, direito,
                    inicio_acesso, fim_acesso, quota, status)
                VALUES (?, ?, ?, ?, 'COMUM', 'ESTACIONAMENTO_EVENTO',
                    '2026-10-10T20:00:00Z', '2026-10-11T04:00:00Z', ?, ?)
                """;
    }

    private Long criarCidade() {
        var pais = paises.criar(new PaisRequest(
                "Brasil Product Constraints", "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest(
                "Parana Product Constraints", "PR", pais.getId(), true));
        return cidades.criar(new CidadeRequest(
                "Curitiba Product Constraints", null, null, estado.getId(), true)).getId();
    }
}
