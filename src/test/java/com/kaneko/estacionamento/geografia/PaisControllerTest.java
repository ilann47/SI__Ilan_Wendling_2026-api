package com.kaneko.estacionamento.geografia;

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
 * Teste de integracao do CRUD de Pais — cobre todos os fluxos: criar, listar,
 * buscar (existente e inexistente), atualizar (existente e inexistente),
 * excluir e os erros de validacao e de unicidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaisControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\" }";
        String body = mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Brasil", "sigla": "BRA", "nacionalidade": "Brasileiro" }
                """;
        String body = mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Brasil"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/paises/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Brasil"))
                .andExpect(jsonPath("$.sigla").value("BRA"));
    }

    @Test
    void listaPaises() throws Exception {
        criar("Brasil");
        criar("Argentina");
        mvc.perform(get("/api/paises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaPais() throws Exception {
        Number id = criar("Brasi");
        String json = """
                { "nome": "Brasil", "sigla": "BRA" }
                """;
        mvc.perform(put("/api/paises/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Brasil"))
                .andExpect(jsonPath("$.sigla").value("BRA"));
    }

    @Test
    void atualizaPaisInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/paises/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiPaisEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/paises/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/paises/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Uruguai");
        mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Uruguai\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/paises/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }
}
