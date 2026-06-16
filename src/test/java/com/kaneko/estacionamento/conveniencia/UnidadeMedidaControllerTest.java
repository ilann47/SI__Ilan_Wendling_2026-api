package com.kaneko.estacionamento.conveniencia;

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
 * Teste de integracao do CRUD de UnidadeMedida — cobre todos os fluxos: criar,
 * listar, buscar (existente e inexistente), atualizar (existente e
 * inexistente), excluir e os erros de validacao e de unicidade da sigla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnidadeMedidaControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome, String sigla) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"sigla\": \"" + sigla + "\" }";
        String body = mvc.perform(post("/api/unidades-medida")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Quilograma", "sigla": "kg" }
                """;
        String body = mvc.perform(post("/api/unidades-medida")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Quilograma"))
                .andExpect(jsonPath("$.sigla").value("KG"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/unidades-medida/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Quilograma"))
                .andExpect(jsonPath("$.sigla").value("KG"));
    }

    @Test
    void listaUnidades() throws Exception {
        criar("Unidade", "UN");
        criar("Litro", "L");
        mvc.perform(get("/api/unidades-medida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaUnidade() throws Exception {
        Number id = criar("Pacot", "PCT");
        String json = """
                { "nome": "Pacote", "sigla": "PCT" }
                """;
        mvc.perform(put("/api/unidades-medida/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pacote"))
                .andExpect(jsonPath("$.sigla").value("PCT"));
    }

    @Test
    void atualizaUnidadeInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/unidades-medida/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"X\", \"sigla\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiUnidadeEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario", "TMP");
        mvc.perform(delete("/api/unidades-medida/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/unidades-medida/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaSiglaDuplicadaCom409() throws Exception {
        criar("Unidade", "UN");
        mvc.perform(post("/api/unidades-medida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Unidades\", \"sigla\": \"un\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/unidades-medida/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeAusente() throws Exception {
        mvc.perform(post("/api/unidades-medida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"sigla\": \"UN\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoSiglaAusente() throws Exception {
        mvc.perform(post("/api/unidades-medida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Unidade\" }"))
                .andExpect(status().isBadRequest());
    }
}
