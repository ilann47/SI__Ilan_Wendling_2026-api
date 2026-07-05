package com.kaneko.estacionamento.logistica;

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
 * Teste de integracao do cadastro de Veiculo de Frota — todos os fluxos, com a
 * unicidade da placa (normalizada em maiusculas).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VeiculoFrotaControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String placa) throws Exception {
        String json = "{ \"placa\": \"" + placa + "\" }";
        String body = mvc.perform(post("/api/veiculos-frota")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"placa\": \"abc1d23\", \"modelo\": \"Sprinter\", \"marca\": \"Mercedes-Benz\","
                + " \"ano\": 2020, \"capacidade\": 1500.00 }";
        String body = mvc.perform(post("/api/veiculos-frota")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.capacidade").value(1500.00))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/veiculos-frota/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("Sprinter"));
    }

    @Test
    void listaVeiculos() throws Exception {
        criar("ABC1D23");
        criar("EFG4H56");
        mvc.perform(get("/api/veiculos-frota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorPlaca() throws Exception {
        criar("ABC1D23");
        criar("EFG4H56");
        mvc.perform(get("/api/veiculos-frota").param("placa", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].placa").value("ABC1D23"));
    }

    @Test
    void atualizaVeiculo() throws Exception {
        Number id = criar("ABC1D23");
        String json = "{ \"placa\": \"ABC1D23\", \"modelo\": \"Daily\", \"capacidade\": 3500.00 }";
        mvc.perform(put("/api/veiculos-frota/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("Daily"));
    }

    @Test
    void atualizaInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/veiculos-frota/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"placa\": \"XYZ9A99\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiVeiculoEdepoisRetorna404() throws Exception {
        Number id = criar("ABC1D23");
        mvc.perform(delete("/api/veiculos-frota/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/veiculos-frota/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaPlacaDuplicadaCom409() throws Exception {
        criar("ABC1D23");
        mvc.perform(post("/api/veiculos-frota")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"placa\": \"abc1d23\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/veiculos-frota/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoPlacaAusente() throws Exception {
        mvc.perform(post("/api/veiculos-frota")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"modelo\": \"Sem Placa\" }"))
                .andExpect(status().isBadRequest());
    }
}
