package com.kaneko.estacionamento.logistica;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de integracao do vinculo transportadora-veiculo (frota) — todos os
 * fluxos, com a unicidade do par e as FKs.
 */
@TenantApiIntegrationTest
class TransportadoraVeiculoFrotaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TransportadoraRepository transportadoraRepository;

    @Autowired
    private VeiculoFrotaRepository veiculoFrotaRepository;

    @Autowired private TenantTestSupport tenant;

    private Long transportadoraId;
    private Long veiculoId;
    private TenantFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Transportadora t = new Transportadora();
        t.setOrganizacaoId(fixture.organizacaoId());
        t.setNome("Rapido Oeste");
        t.setDocumento("44555666000177");
        transportadoraId = transportadoraRepository.save(t).getId();

        VeiculoFrota v = new VeiculoFrota();
        v.setOrganizacaoId(fixture.organizacaoId());
        v.setPlaca("ABC1D23");
        veiculoId = veiculoFrotaRepository.save(v).getId();
    }

    private Number criar() throws Exception {
        String json = "{ \"transportadoraId\": " + transportadoraId + ", \"veiculoFrotaId\": " + veiculoId + " }";
        String body = mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        Number id = criar();
        mvc.perform(get("/api/transportadora-veiculos/" + id).header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportadoraNome").value("Rapido Oeste"))
                .andExpect(jsonPath("$.veiculoFrotaPlaca").value("ABC1D23"));
    }

    @Test
    void listaVinculos() throws Exception {
        criar();
        mvc.perform(get("/api/transportadora-veiculos").header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void excluiVinculoEdepoisRetorna404() throws Exception {
        Number id = criar();
        mvc.perform(delete("/api/transportadora-veiculos/" + id).header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/transportadora-veiculos/" + id).header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaParDuplicadoCom409() throws Exception {
        criar();
        String json = "{ \"transportadoraId\": " + transportadoraId + ", \"veiculoFrotaId\": " + veiculoId + " }";
        mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoTransportadoraNaoExiste() throws Exception {
        String json = "{ \"transportadoraId\": 999999, \"veiculoFrotaId\": " + veiculoId + " }";
        mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoVeiculoAusente() throws Exception {
        String json = "{ \"transportadoraId\": " + transportadoraId + " }";
        mvc.perform(post("/api/transportadora-veiculos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
