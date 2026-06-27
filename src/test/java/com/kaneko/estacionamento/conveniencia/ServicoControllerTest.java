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
 * Teste de integracao do CRUD de Servico — cobre todos os fluxos: criar, listar,
 * buscar (existente e inexistente), atualizar (existente e inexistente),
 * excluir e os erros de validacao e de unicidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServicoControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\" }";
        String body = mvc.perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Lavagem Simples", "descricao": "Lavagem externa", "valorPadrao": 25.00 }
                """;
        String body = mvc.perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Lavagem Simples"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/servicos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lavagem Simples"))
                .andExpect(jsonPath("$.descricao").value("Lavagem externa"));
    }

    @Test
    void listaServicos() throws Exception {
        criar("Lavagem");
        criar("Calibragem");
        mvc.perform(get("/api/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Lavagem");
        criar("Calibragem");

        mvc.perform(get("/api/servicos").param("nome", "lavag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Lavagem"));
    }

    @Test
    void atualizaServico() throws Exception {
        Number id = criar("Lavage");
        String json = """
                { "nome": "Lavagem", "descricao": "Lavagem completa" }
                """;
        mvc.perform(put("/api/servicos/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lavagem"))
                .andExpect(jsonPath("$.descricao").value("Lavagem completa"));
    }

    @Test
    void atualizaServicoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/servicos/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiServicoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/servicos/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/servicos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Polimento");
        mvc.perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Polimento\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/servicos/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        mvc.perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoValorNegativo() throws Exception {
        mvc.perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Cera\", \"valorPadrao\": -5.00 }"))
                .andExpect(status().isBadRequest());
    }
}
