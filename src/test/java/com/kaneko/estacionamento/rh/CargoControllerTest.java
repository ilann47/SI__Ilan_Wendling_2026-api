package com.kaneko.estacionamento.rh;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Cargo — todos os fluxos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CargoControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"salarioBase\": 2000.00 }";
        String body = mvc.perform(post("/api/cargos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Operador\", \"descricao\": \"Atende o patio\", \"salarioBase\": 2000.00, \"requerCnh\": false }";
        String body = mvc.perform(post("/api/cargos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Operador"))
                .andExpect(jsonPath("$.salarioBase").value(2000.00))
                .andExpect(jsonPath("$.requerCnh").value(false))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/cargos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Atende o patio"));
    }

    @Test
    void listaCargos() throws Exception {
        criar("Operador");
        criar("Gerente");
        mvc.perform(get("/api/cargos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaCargo() throws Exception {
        Number id = criar("Auxiliar");
        String json = "{ \"nome\": \"Auxiliar Senior\", \"salarioBase\": 2500.00, \"requerCnh\": true }";
        mvc.perform(put("/api/cargos/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Auxiliar Senior"))
                .andExpect(jsonPath("$.requerCnh").value(true));
    }

    @Test
    void atualizaCargoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/cargos/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiCargoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/cargos/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cargos/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Unico");
        mvc.perform(post("/api/cargos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Unico\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/cargos/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeAusente() throws Exception {
        mvc.perform(post("/api/cargos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"salarioBase\": 100 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoSalarioNegativo() throws Exception {
        mvc.perform(post("/api/cargos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Negativo\", \"salarioBase\": -1 }"))
                .andExpect(status().isBadRequest());
    }
}
