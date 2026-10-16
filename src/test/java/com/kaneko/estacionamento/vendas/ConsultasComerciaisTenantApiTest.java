package com.kaneko.estacionamento.vendas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class ConsultasComerciaisTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenants;
    @Autowired ClienteRepository clientes;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;
    @Autowired ServicoRepository servicos;
    @Autowired LocalEstoqueRepository locais;

    @ParameterizedTest
    @ValueSource(strings = {"administrative-sales", "service-orders"})
    void filtraNumeroESituacaoAntesDaPaginacaoComTotalEIsolamento(String modulo) throws Exception {
        var a = contexto(modulo);
        var b = contexto(modulo);
        String rota = "/api/v1/" + modulo;
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

    private Contexto contexto(String modulo) throws Exception {
        var tenant = tenants.criarTenantAdmin();
        Long org = tenant.organizacaoId();
        var cliente = new Cliente();
        cliente.setOrganizacaoId(org);
        cliente.setNome("Cliente consulta");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        Long clienteId = clientes.saveAndFlush(cliente).getId();
        if ("service-orders".equals(modulo)) {
            var servico = new Servico();
            servico.setOrganizacaoId(org);
            servico.setNome("Serviço consulta");
            servico.setValorPadrao(BigDecimal.TEN);
            return new Contexto(tenant, clienteId, servicos.saveAndFlush(servico).getId(), null);
        }
        var marca = new Marca();
        marca.setOrganizacaoId(org);
        marca.setNome("Marca consulta");
        var unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org);
        unidade.setNome("Unidade consulta");
        unidade.setSigla("UN");
        var produto = new Produto();
        produto.setOrganizacaoId(org);
        produto.setNome("Produto consulta");
        produto.setMarca(marcas.saveAndFlush(marca));
        produto.setUnidadeMedida(unidades.saveAndFlush(unidade));
        Long produtoId = produtos.saveAndFlush(produto).getId();
        Long localId = locais.saveAndFlush(LocalEstoque.criar(org, "Local consulta")).getId();
        return new Contexto(tenant, clienteId, produtoId, localId);
    }

    private long criar(String rota, Contexto contexto, String numero) throws Exception {
        String tipoItem = contexto.localId() == null ? "servicoId" : "produtoId";
        String body = "{\"numero\":\"" + numero + "\",\"clienteId\":" + contexto.clienteId()
                + (contexto.localId() == null ? "" : ",\"localEstoqueId\":" + contexto.localId())
                + ",\"moeda\":\"BRL\",\"itens\":[{\"" + tipoItem + "\":" + contexto.itemId()
                + ",\"quantidade\":1,\"valorUnitario\":10}]}";
        String response = mvc.perform(post(rota)
                .header(HttpHeaders.AUTHORIZATION, contexto.tenant().tokenContextual())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private record Contexto(TenantFixture tenant, Long clienteId, Long itemId, Long localId) {}
}
