package com.kaneko.estacionamento.financeiro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do titulo a receber — lancamento manual, consulta, baixa
 * parcial/total e cancelamento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaReceberControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ClienteRepository clienteRepository;

    private Long clienteId;

    @BeforeEach
    void setup() {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente B");
        cliente.setDocumento("98765432100");
        clienteId = clienteRepository.save(cliente).getId();
    }

    private Number criar(String valorOriginal) throws Exception {
        String json = "{ \"clienteId\": " + clienteId + ", \"valorOriginal\": " + valorOriginal
                + ", \"dataVencimento\": \"2030-12-31\" }";
        String body = mvc.perform(post("/api/contas-receber")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaTituloManualEbuscaPorId() throws Exception {
        Number id = criar("600.00");
        mvc.perform(get("/api/contas-receber/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNome").value("Cliente B"))
                .andExpect(jsonPath("$.valorTotal").value(600.00))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"));
    }

    @Test
    void listaTitulos() throws Exception {
        criar("100.00");
        criar("200.00");
        mvc.perform(get("/api/contas-receber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void baixaParcialMudaSituacaoParaParcial() throws Exception {
        Number id = criar("1000.00");
        mvc.perform(post("/api/contas-receber/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 300.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PARCIAL"))
                .andExpect(jsonPath("$.valorRecebido").value(300.00));
    }

    @Test
    void baixaTotalMudaSituacaoParaRecebida() throws Exception {
        Number id = criar("500.00");
        mvc.perform(post("/api/contas-receber/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 500.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("RECEBIDA"))
                .andExpect(jsonPath("$.dataRecebimento").isNotEmpty());
    }

    @Test
    void rejeitaBaixaQueExcedeSaldoCom409() throws Exception {
        Number id = criar("100.00");
        mvc.perform(post("/api/contas-receber/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 200.00 }"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelaTitulo() throws Exception {
        Number id = criar("50.00");
        mvc.perform(post("/api/contas-receber/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
    }

    @Test
    void retorna404QuandoClienteNaoExiste() throws Exception {
        String json = "{ \"clienteId\": 999999, \"valorOriginal\": 100.00, \"dataVencimento\": \"2030-12-31\" }";
        mvc.perform(post("/api/contas-receber")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/contas-receber/999999")).andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoClienteOuValorAusente() throws Exception {
        mvc.perform(post("/api/contas-receber")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"dataVencimento\": \"2030-12-31\" }"))
                .andExpect(status().isBadRequest());
    }
}
