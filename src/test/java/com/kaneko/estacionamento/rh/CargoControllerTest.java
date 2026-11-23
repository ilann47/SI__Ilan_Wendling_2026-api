package com.kaneko.estacionamento.rh;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de integracao do CRUD de Cargo — todos os fluxos.
 */
@TenantApiIntegrationTest
class CargoControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TenantTestSupport tenant;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        token = tenant.criarTenantAdmin().tokenContextual();
    }

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"salarioBase\": 2000.00 }";
        String body = mvc.perform(post("/api/cargos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Operador\", \"descricao\": \"Atende o patio\", \"salarioBase\": 2000.00, \"requerCnh\": false }";
        String body = mvc.perform(post("/api/cargos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Operador"))
                .andExpect(jsonPath("$.salarioBase").value(2000.00))
                .andExpect(jsonPath("$.requerCnh").value(false))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/cargos/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Atende o patio"));
    }

    @Test
    void listaCargos() throws Exception {
        criar("Operador");
        criar("Gerente");
        mvc.perform(get("/api/cargos").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Operador");
        criar("Gerente");
        mvc.perform(get("/api/cargos").param("nome", "geren")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Gerente"));
    }

    @Test
    void atualizaCargo() throws Exception {
        Number id = criar("Auxiliar");
        String json = "{ \"nome\": \"Auxiliar Senior\", \"salarioBase\": 2500.00, \"requerCnh\": true }";
        mvc.perform(put("/api/cargos/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Auxiliar Senior"))
                .andExpect(jsonPath("$.requerCnh").value(true));
    }

    @Test
    void atualizaCargoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/cargos/999999")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiCargoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/cargos/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cargos/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Unico");
        mvc.perform(post("/api/cargos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Unico\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/cargos/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeAusente() throws Exception {
        mvc.perform(post("/api/cargos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"salarioBase\": 100 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoSalarioNegativo() throws Exception {
        mvc.perform(post("/api/cargos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Negativo\", \"salarioBase\": -1 }"))
                .andExpect(status().isBadRequest());
    }
}
