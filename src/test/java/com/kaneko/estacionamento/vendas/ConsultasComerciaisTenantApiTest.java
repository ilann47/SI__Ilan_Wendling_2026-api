package com.kaneko.estacionamento.vendas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class ConsultasComerciaisTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenants;
    @Autowired ClienteRepository clientes;
    @Autowired ServicoRepository servicos;

    @Test
    void filtraNumeroESituacaoAntesDaPaginacaoComTotalEIsolamento() throws Exception {
        var a = contexto();
        var b = contexto();
        String rota = "/api/v1/service-orders";
        long primeiro = criar(rota, a, "BUSCA-01");
        long segundo = criar(rota, a, "BUSCA-02");
        long cancelado = criar(rota, a, "BUSCA-03");
        criar(rota, b, "BUSCA-01");
        mvc.perform(post(rota + "/{id}/cancellation", cancelado)
                .header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cancelamento de teste\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .param("numero", "  busca-  ").param("status", "RASCUNHO")
                .param("page", "0").param("size", "1").param("sort", "numero,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(primeiro));
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .param("numero", "busca-").param("status", "RASCUNHO")
                .param("page", "1").param("size", "1").param("sort", "numero,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segundo));
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .param("numero", "busca-").param("status", "RASCUNHO")
                .param("page", "5").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, b.tenant().tokenContextual())
                .param("numero", "busca-").param("status", "RASCUNHO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .param("status", "CANCELADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelado));

        long literal = criar(rota, a, "LITERAL_%");
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .param("numero", "_%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(literal));
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, a.tenant().tokenContextual())
                .param("status", "DESCONHECIDO"))
                .andExpect(status().isBadRequest());
    }

    private Contexto contexto() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        Long org = tenant.organizacaoId();
        var cliente = new Cliente();
        cliente.setOrganizacaoId(org);
        cliente.setNome("Cliente consulta");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        Long clienteId = clientes.saveAndFlush(cliente).getId();
        var servico = new Servico();
        servico.setOrganizacaoId(org);
        servico.setNome("Serviço consulta");
        servico.setValorPadrao(BigDecimal.TEN);
        return new Contexto(tenant, clienteId, servicos.saveAndFlush(servico).getId());
    }

    private long criar(String rota, Contexto contexto, String numero) throws Exception {
        String body = "{\"numero\":\"" + numero + "\",\"clienteId\":" + contexto.clienteId()
                + ",\"moeda\":\"BRL\",\"itens\":[{\"servicoId\":" + contexto.itemId()
                + ",\"quantidade\":1,\"valorUnitario\":10}]}";
        String response = mvc.perform(post(rota)
                .header(HttpHeaders.AUTHORIZATION, contexto.tenant().tokenContextual())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private record Contexto(TenantFixture tenant, Long clienteId, Long itemId) {}
}
