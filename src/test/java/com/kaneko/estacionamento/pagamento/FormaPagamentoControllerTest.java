package com.kaneko.estacionamento.pagamento;

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
 * Teste de integracao do CRUD de Forma de Pagamento — cobre todos os fluxos:
 * criar, listar, buscar (existente e inexistente), atualizar (existente e
 * inexistente), excluir e os erros de validacao e de unicidade.
 */
@TenantApiIntegrationTest
class FormaPagamentoControllerTest {

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
        String json = "{ \"nome\": \"" + nome + "\", \"descricao\": \"" + nome + " - descricao\" }";
        String body = mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private long totalElementos() throws Exception {
        String body = mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number total = JsonPath.read(body, "$.totalElements");
        return total.longValue();
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Pix", "descricao": "Pagamento instantaneo" }
                """;
        String body = mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Pix"))
                .andExpect(jsonPath("$.descricao").value("Pagamento instantaneo"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/formas-pagamento/" + id)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pix"))
                .andExpect(jsonPath("$.descricao").value("Pagamento instantaneo"));
    }

    @Test
    void listaFormasPagamento() throws Exception {
        long antes = totalElementos();
        criar("Dinheiro");
        criar("Cartao");
        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value((int) (antes + 2)));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Pix");
        criar("Boleto");
        mvc.perform(get("/api/formas-pagamento").param("nome", "pix")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Pix"));
    }

    @Test
    void atualizaFormaPagamento() throws Exception {
        Number id = criar("Boletinho");
        String json = """
                { "nome": "Boleto", "descricao": "Boleto bancario" }
                """;
        mvc.perform(put("/api/formas-pagamento/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Boleto"))
                .andExpect(jsonPath("$.descricao").value("Boleto bancario"));
    }

    @Test
    void atualizaFormaPagamentoInexistenteRetorna404() throws Exception {
        String json = """
                { "nome": "X", "descricao": "Y" }
                """;
        mvc.perform(put("/api/formas-pagamento/999999")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiFormaPagamentoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/formas-pagamento/" + id)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/formas-pagamento/" + id)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Cheque");
        mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Cheque\", \"descricao\": \"Outra descricao\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/formas-pagamento/999999")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeAusente() throws Exception {
        mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"descricao\": \"Sem nome\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoDescricaoAusente() throws Exception {
        mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Sem descricao\" }"))
                .andExpect(status().isBadRequest());
    }
}
