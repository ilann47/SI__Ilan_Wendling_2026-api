package com.kaneko.estacionamento.instalacoes;

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
class InstalacoesRepositoryConstraintTest {

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
                "12.345.678/0001-95", "Kaneko Constraints Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        var pais = paises.criar(new PaisRequest("Brasil Constraints", "BX", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest("Parana Constraints", "PX", pais.getId(), true));
        cidadeId = cidades.criar(new CidadeRequest(
                "Curitiba Constraints", null, null, estado.getId(), true)).getId();
    }

    @Test
    void bancoImpedeNomeNormalizadoDuplicadoNoMesmoTenant() {
        inserir("Patio Constraint", 100, 80);

        assertThatThrownBy(() -> inserir("  patio constraint  ", 100, 80))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeCapacidadeOperacionalAcimaDaFisica() {
        assertThatThrownBy(() -> inserir("Patio Invalido", 100, 101))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoProtegeCodigoNormalizadoDoSetor() {
        long patioId = inserirRetornandoId("Patio Setores", 100, 90);
        inserirSetor(patioId, "NORTE", 40, "{COMUM,PCD}");

        assertThatThrownBy(() -> inserirSetor(patioId, " norte ", 20, "{VIP}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoProtegeCategoriasDoSetor() {
        long patioId = inserirRetornandoId("Patio Categorias", 100, 90);

        assertThatThrownBy(() -> inserirSetor(patioId, "SUL", 20, "{INVALIDA}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoProtegeCapacidadePositivaDoSetor() {
        long patioId = inserirRetornandoId("Patio Capacidade Setor", 100, 90);

        assertThatThrownBy(() -> inserirSetor(patioId, "LESTE", 0, "{COMUM}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(String nome, int fisica, int operacional) {
        jdbc.update("""
                INSERT INTO patio (
                    organizacao_id, nome, endereco, bairro, cep, cidade_id,
                    capacidade_vagas, capacidade_operacional, ativo)
                VALUES (?, ?, 'Rua Constraint', 'Centro', '80000-000', ?, ?, ?, true)
                """, organizacaoId, nome, cidadeId, fisica, operacional);
    }

    private long inserirRetornandoId(String nome, int fisica, int operacional) {
        return jdbc.queryForObject("""
                INSERT INTO patio (
                    organizacao_id, nome, endereco, bairro, cep, cidade_id,
                    capacidade_vagas, capacidade_operacional, ativo)
                VALUES (?, ?, 'Rua Constraint', 'Centro', '80000-000', ?, ?, ?, true)
                RETURNING id
                """, Long.class, organizacaoId, nome, cidadeId, fisica, operacional);
    }

    private void inserirSetor(long patioId, String codigo, int capacidade, String categorias) {
        jdbc.update("""
                INSERT INTO setor (
                    organizacao_id, patio_id, codigo, nome, capacidade, categorias, ativo)
                VALUES (?, ?, ?, 'Setor Constraint', ?, ?::varchar[], true)
                """, organizacaoId, patioId, codigo, capacidade, categorias);
    }
}
