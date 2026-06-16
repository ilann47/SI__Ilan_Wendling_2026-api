package com.kaneko.estacionamento.fiscal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Teste de integracao da nota de servico — calculo de ISS, emissao,
 * cancelamento e demais fluxos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotaServicoControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ClienteRepository clienteRepository;

    private Long clienteId;

    @BeforeEach
    void setup() {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente C");
        cliente.setDocumento("11122233344");
        clienteId = clienteRepository.save(cliente).getId();
    }

    private String corpo(String numero, String valorServico, String aliquota, String desconto) {
        return "{ \"numero\": \"" + numero + "\", \"clienteId\": " + clienteId
                + ", \"valorServico\": " + valorServico + ", \"aliquotaIss\": " + aliquota
                + ", \"valorDesconto\": " + desconto + " }";
    }

    private Number criar(String numero) throws Exception {
        String body = mvc.perform(post("/api/notas-servico")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(numero, "100.00", "5.00", "0")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaCalculaIssEbuscaPorId() throws Exception {
        Number id = criar("NS1");
        mvc.perform(get("/api/notas-servico/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.valorIss").value(5.00))
                .andExpect(jsonPath("$.valorTotal").value(100.00));
    }

    @Test
    void atualizaNotaPendente() throws Exception {
        Number id = criar("NS2");
        mvc.perform(put("/api/notas-servico/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NS2", "200.00", "2.00", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorIss").value(4.00))
                .andExpect(jsonPath("$.valorTotal").value(200.00));
    }

    @Test
    void emiteNota() throws Exception {
        Number id = criar("NS3");
        mvc.perform(post("/api/notas-servico/" + id + "/emissao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("EMITIDA"));
    }

    @Test
    void naoEmiteNotaJaEmitida() throws Exception {
        Number id = criar("NS4");
        mvc.perform(post("/api/notas-servico/" + id + "/emissao")).andExpect(status().isOk());
        mvc.perform(post("/api/notas-servico/" + id + "/emissao")).andExpect(status().isConflict());
    }

    @Test
    void cancelaNota() throws Exception {
        Number id = criar("NS5");
        mvc.perform(post("/api/notas-servico/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
    }

    @Test
    void rejeitaDescontoMaiorQueServico() throws Exception {
        mvc.perform(post("/api/notas-servico")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NS6", "100.00", "5.00", "150.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void excluiNotaPendenteEdepoisRetorna404() throws Exception {
        Number id = criar("NS7");
        mvc.perform(delete("/api/notas-servico/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notas-servico/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void naoExcluiNotaEmitida() throws Exception {
        Number id = criar("NS8");
        mvc.perform(post("/api/notas-servico/" + id + "/emissao")).andExpect(status().isOk());
        mvc.perform(delete("/api/notas-servico/" + id)).andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/notas-servico/999999")).andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoSemCliente() throws Exception {
        mvc.perform(post("/api/notas-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"NSX\", \"valorServico\": 100.00 }"))
                .andExpect(status().isBadRequest());
    }
}
