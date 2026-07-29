package com.kaneko.estacionamento.organizacao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrganizacaoControllerTest {

    private static final String CRIACAO = """
            {
              "document": "12.345.678/0001-95",
              "legalName": "Kaneko Estacionamentos Ltda",
              "tradeName": "Kaneko",
              "currency": "brl",
              "timeZone": "America/Sao_Paulo",
              "region": "br-pr",
              "plan": "enterprise"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void criaEConsultaOrganizacaoComLocationEETag() throws Exception {
        var criacao = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CRIACAO))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.document").value("12345678000195"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.region").value("BR-PR"))
                .andExpect(jsonPath("$.plan").value("ENTERPRISE"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        Number id = JsonPath.read(criacao.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/organizations/{id}", id.longValue()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.legalName").value("Kaneko Estacionamentos Ltda"));
    }

    @Test
    void atualizaSomenteCamposPermitidosComVersaoEsperada() throws Exception {
        long id = criarOrganizacao();
        String patch = """
                {
                  "tradeName": "Kaneko Eventos",
                  "currency": "usd",
                  "timeZone": "America/New_York",
                  "region": "us-ny",
                  "plan": "enterprise_plus"
                }
                """;

        mvc.perform(patch("/api/v1/organizations/{id}", id)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.tradeName").value("Kaneko Eventos"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.document").value("12345678000195"))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test
    void exigeIfMatchERetorna412ParaVersaoDivergente() throws Exception {
        long id = criarOrganizacao();
        String patch = "{ \"tradeName\": \"Novo Nome\" }";

        mvc.perform(patch("/api/v1/organizations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isPreconditionRequired());

        mvc.perform(patch("/api/v1/organizations/{id}", id)
                        .header("If-Match", "\"99\"")
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchNaoAceitaDocumentoEstadoIdOuVersao() throws Exception {
        long id = criarOrganizacao();
        String patch = """
                { "document": "45.723.174/0001-10", "status": "ENCERRADA", "version": 0, "id": 999 }
                """;

        mvc.perform(patch("/api/v1/organizations/{id}", id)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaDocumentoDuplicadoEValoresInvalidos() throws Exception {
        criarOrganizacao();

        mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON).content(CRIACAO))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CRIACAO.replace("America/Sao_Paulo", "Sao_Paulo")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna404QuandoOrganizacaoNaoExiste() throws Exception {
        mvc.perform(get("/api/v1/organizations/999999"))
                .andExpect(status().isNotFound());
    }

    private long criarOrganizacao() throws Exception {
        String body = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON).content(CRIACAO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
}
