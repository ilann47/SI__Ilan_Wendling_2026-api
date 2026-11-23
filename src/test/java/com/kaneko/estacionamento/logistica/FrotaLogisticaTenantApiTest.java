package com.kaneko.estacionamento.logistica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class FrotaLogisticaTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransportadoraRepository transportadoras;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long transportadoraA;
    private Long transportadoraB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        transportadoraA = criarTransportadora(tenantA.organizacaoId(), "Transportadora A", "11111111111");
        transportadoraB = criarTransportadora(tenantB.organizacaoId(), "Transportadora B", "22222222222");
    }

    @Test
    void exigeContextoEPermissaoNasDuasRotas() throws Exception {
        mvc.perform(get("/api/veiculos-frota")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transportadora-veiculos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/veiculos-frota").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "frota_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/veiculos-frota").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transportadora-veiculos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transportadoraId\":" + transportadoraA + ",\"veiculoFrotaId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteMesmaPlacaEmTenantsESegregaVeiculo() throws Exception {
        long veiculoA = criarVeiculo(tenantA, " abc1d23 ");
        long veiculoB = criarVeiculo(tenantB, "ABC1D23");

        mvc.perform(get("/api/veiculos-frota/{id}", veiculoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/veiculos-frota").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(veiculoA));
        mvc.perform(put("/api/veiculos-frota/{id}", veiculoB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placa\":\"INV1A00\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/veiculos-frota/{id}", veiculoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void vinculoExigeAsDuasPontasDoTenantESegregaAcesso() throws Exception {
        long veiculoA = criarVeiculo(tenantA, "AAA1A11");
        long veiculoB = criarVeiculo(tenantB, "BBB2B22");
        long vinculoB = criarVinculo(tenantB, transportadoraB, veiculoB);

        mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vinculoPayload(transportadoraB, veiculoA)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vinculoPayload(transportadoraA, veiculoB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transportadora-veiculos/{id}", vinculoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/transportadora-veiculos/{id}", vinculoB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vinculoPayload(transportadoraA, veiculoA)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/transportadora-veiculos/{id}", vinculoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transportadora-veiculos")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejeitaOrganizacaoECamposDesconhecidos() throws Exception {
        mvc.perform(post("/api/veiculos-frota").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"AAA1A11\",\"organizationId\":" + tenantB.organizacaoId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transportadora-veiculos").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transportadoraId\":" + transportadoraA
                                + ",\"veiculoFrotaId\":1,\"campoInventado\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoPermaneceInvisivel() throws Exception {
        Long transportadora = jdbc.queryForObject("""
                INSERT INTO transportadora (nome, tipo, documento, ativo, created_at, updated_at)
                VALUES ('Historica', 'JURIDICA', '33333333333', true, now(), now()) RETURNING id
                """, Long.class);
        Long veiculo = jdbc.queryForObject("""
                INSERT INTO veiculo_frota (placa, ativo, created_at, updated_at)
                VALUES ('HIS1A11', true, now(), now()) RETURNING id
                """, Long.class);
        Long vinculo = jdbc.queryForObject("""
                INSERT INTO transportadora_veiculo_frota
                    (transportadora_id, veiculo_frota_id, ativo, created_at, updated_at)
                VALUES (?, ?, true, now(), now()) RETURNING id
                """, Long.class, transportadora, veiculo);

        mvc.perform(get("/api/veiculos-frota/{id}", veiculo)
                        .header("Authorization", tenantA.tokenContextual())).andExpect(status().isNotFound());
        mvc.perform(get("/api/transportadora-veiculos/{id}", vinculo)
                        .header("Authorization", tenantA.tokenContextual())).andExpect(status().isNotFound());
    }

    @Test
    void auditaVeiculoSemPlacaEObservacaoEVinculoComIds() throws Exception {
        long veiculo = criarVeiculo(tenantA, "AUD1A11");
        long vinculo = criarVinculo(tenantA, transportadoraA, veiculo);
        mvc.perform(put("/api/veiculos-frota/{id}", veiculo)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"AUD1A11\",\"modelo\":\"Atualizado\","
                                + "\"observacao\":\"continua privada\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/transportadora-veiculos/{id}", vinculo)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vinculoPayload(transportadoraA, veiculo)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/veiculos-frota/{id}", veiculo)
                        .header("Authorization", tenantA.tokenContextual())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/transportadora-veiculos/{id}", vinculo)
                        .header("Authorization", tenantA.tokenContextual())).andExpect(status().isNoContent());

        Integer veiculoAuditado = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND ator_id=?
                  AND recurso_tipo='VEICULO_FROTA' AND recurso_id=?
                  AND classe_retencao='PRIVACIDADE'
                  AND coalesce(estado_anterior::text,'') NOT LIKE '%AUD1A11%'
                  AND coalesce(estado_posterior::text,'') NOT LIKE '%AUD1A11%'
                  AND coalesce(estado_anterior::text,'') NOT LIKE '%observacao%'
                  AND coalesce(estado_posterior::text,'') NOT LIKE '%observacao%'
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), veiculo);
        Integer vinculoAuditado = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND ator_id=?
                  AND recurso_tipo='TRANSPORTADORA_VEICULO_FROTA'
                  AND recurso_id=? AND classe_retencao='CONFIGURACAO'
                  AND estado_posterior::text LIKE '%transportadoraId%'
                  AND estado_posterior::text LIKE '%veiculoFrotaId%'
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), vinculo);
        Integer chavesVeiculo = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE recurso_tipo='VEICULO_FROTA' AND recurso_id=?
                  AND acao='VEICULO_FROTA_ATUALIZADO'
                  AND (SELECT count(*) FROM jsonb_object_keys(estado_anterior))=5
                  AND (SELECT count(*) FROM jsonb_object_keys(estado_posterior))=5
                  AND jsonb_exists_all(estado_anterior,
                      array['modelo','marca','ano','capacidade','ativo'])
                  AND jsonb_exists_all(estado_posterior,
                      array['modelo','marca','ano','capacidade','ativo'])
                """, Integer.class, veiculo);
        Integer chavesVinculo = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE recurso_tipo='TRANSPORTADORA_VEICULO_FROTA' AND recurso_id=?
                  AND acao='FROTA_TRANSPORTADORA_ATUALIZADA'
                  AND (SELECT count(*) FROM jsonb_object_keys(estado_anterior))=3
                  AND (SELECT count(*) FROM jsonb_object_keys(estado_posterior))=3
                  AND jsonb_exists_all(estado_anterior,
                      array['transportadoraId','veiculoFrotaId','ativo'])
                  AND jsonb_exists_all(estado_posterior,
                      array['transportadoraId','veiculoFrotaId','ativo'])
                """, Integer.class, vinculo);
        assertThat(veiculoAuditado).isEqualTo(3);
        assertThat(vinculoAuditado).isEqualTo(3);
        assertThat(chavesVeiculo).isEqualTo(1);
        assertThat(chavesVinculo).isEqualTo(1);
    }

    private long criarVeiculo(TenantFixture fixture, String placa) throws Exception {
        String body = mvc.perform(post("/api/veiculos-frota")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"" + placa + "\",\"observacao\":\"privada\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarVinculo(TenantFixture fixture, Long transportadora, Long veiculo) throws Exception {
        String body = mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vinculoPayload(transportadora, veiculo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long criarTransportadora(Long organizacaoId, String nome, String documento) {
        Transportadora transportadora = new Transportadora();
        transportadora.setOrganizacaoId(organizacaoId);
        transportadora.setNome(nome);
        transportadora.setDocumento(documento);
        return transportadoras.saveAndFlush(transportadora).getId();
    }

    private static String vinculoPayload(Long transportadora, Long veiculo) {
        return "{\"transportadoraId\":" + transportadora
                + ",\"veiculoFrotaId\":" + veiculo + "}";
    }
}
