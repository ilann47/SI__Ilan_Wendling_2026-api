package com.kaneko.estacionamento.fiscal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
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
class NotaSaidaTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired ClienteRepository clientes;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;

    private TenantFixture fixture;
    private Long clienteId;
    private Long produtoId;
    private Long localId;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(fixture.organizacaoId());
        cliente.setNome("Cliente saida");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        clienteId = clientes.saveAndFlush(cliente).getId();
        Marca marca = new Marca(); marca.setOrganizacaoId(fixture.organizacaoId());
        marca.setNome("Marca saida"); marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida(); unidade.setOrganizacaoId(fixture.organizacaoId());
        unidade.setNome("Unidade saida"); unidade.setSigla("NS"); unidade = unidades.save(unidade);
        Produto produto = new Produto(); produto.setOrganizacaoId(fixture.organizacaoId());
        produto.setNome("Produto saida"); produto.setMarca(marca); produto.setUnidadeMedida(unidade);
        produtoId = produtos.saveAndFlush(produto).getId();
        localId = id(post("/api/v1/stock-locations"), "{\"nome\":\"Expedicao\"}");
        mvc.perform(post("/api/v1/stock-adjustments")
                .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                .header("Idempotency-Key", "carga-inicial")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\":" + produtoId + ",\"localEstoqueId\":" + localId
                        + ",\"delta\":10,\"motivo\":\"Carga inicial\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void confirmaSaidaBaixaRazaoEGeraContaReceber() throws Exception {
        long nota = id(post("/api/notas-saida"), "{\"numero\":\"NS-1\",\"clienteId\":"
                + clienteId + ",\"localEstoqueId\":" + localId + ",\"itens\":[{\"produtoId\":"
                + produtoId + ",\"quantidade\":4,\"valorUnitario\":8}]}");
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CONFIRMADA"));
        mvc.perform(get("/api/v1/stock-balances")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .param("produtoId", produtoId.toString()).param("localEstoqueId", localId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quantidade").value(6));
        mvc.perform(get("/api/contas-receber")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].notaSaidaId").value(nota));
    }

    private long id(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String payload) throws Exception {
        String body = mvc.perform(request.header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
