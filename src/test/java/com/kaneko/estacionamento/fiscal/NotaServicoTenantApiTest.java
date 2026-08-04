package com.kaneko.estacionamento.fiscal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class NotaServicoTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired ClienteRepository clientes;
    private TenantFixture fixture;
    private Long clienteId;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(fixture.organizacaoId());
        cliente.setNome("Cliente servico");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        clienteId = clientes.saveAndFlush(cliente).getId();
    }

    @Test
    void emiteNotaTenantEGeraContaReceber() throws Exception {
        String body = mvc.perform(post("/api/notas-servico")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NFS-1\",\"clienteId\":" + clienteId
                                + ",\"valorServico\":150,\"aliquotaIss\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizacaoId").value(fixture.organizacaoId()))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(body, "$.id").longValue();
        mvc.perform(post("/api/notas-servico/{id}/emissao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("EMITIDA"));
        mvc.perform(get("/api/contas-receber")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].notaServicoId").value(notaId))
                .andExpect(jsonPath("$.content[0].valorTotal").value(150));
    }
}
