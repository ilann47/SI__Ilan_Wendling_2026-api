package com.kaneko.estacionamento.vendas;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TenantApiIntegrationTest
class VendaFluxoConcorrenciaTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenants;
    @Autowired ClienteRepository clientes;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;
    @Autowired LocalEstoqueRepository locais;
    @Autowired JdbcTemplate jdbc;

    private TenantFixture tenant;
    private Long produtoId;
    private Long clienteId;
    private Long localId;

    @BeforeEach
    void contexto() throws Exception {
        tenant = tenants.criarTenantAdmin();
        Long org = tenant.organizacaoId();
        var cliente = new Cliente();
        cliente.setOrganizacaoId(org);
        cliente.setNome("Cliente concorrência vendas");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        clienteId = clientes.saveAndFlush(cliente).getId();
        var marca = new Marca();
        marca.setOrganizacaoId(org);
        marca.setNome("Marca concorrência vendas");
        var unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org);
        unidade.setNome("Unidade concorrência vendas");
        unidade.setSigla("UN");
        var produto = new Produto();
        produto.setOrganizacaoId(org);
        produto.setNome("Produto concorrência vendas");
        produto.setMarca(marcas.saveAndFlush(marca));
        produto.setUnidadeMedida(unidades.saveAndFlush(unidade));
        produtoId = produtos.saveAndFlush(produto).getId();
        localId = locais.saveAndFlush(LocalEstoque.criar(org, "Local concorrência vendas")).getId();
        mvc.perform(post("/api/v1/stock-adjustments")
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual())
                .header("Idempotency-Key", "saldo-inicial")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\":" + produtoId + ",\"localEstoqueId\":" + localId
                        + ",\"delta\":10,\"motivo\":\"Saldo inicial do teste\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void confirmacoesConcorrentesDaMesmaVersaoNaoDuplicamBaixaNemTitulos() throws Exception {
        long vendaId = criarVenda();
        var responses = simultaneas(() -> confirmar(vendaId), () -> confirmar(vendaId));
        var statuses = responses.stream().map(MockHttpServletResponse::getStatus).toList();
        assertThat(statuses).contains(200);
        assertThat(statuses.stream().filter(code -> code == 200)).hasSize(1);
        assertThat(statuses.stream().filter(code -> code != 200).toList()).allMatch(code -> code == 412 || code == 409);
        verificarUmaConfirmacao(vendaId);
        mvc.perform(get("/api/v1/administrative-sales/{id}", vendaId)
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duasChavesConcorrentesGeramSomenteUmaNotaDoPedido() throws Exception {
        long vendaId = criarVenda();
        assertThat(confirmar(vendaId).getStatus()).isEqualTo(200);
        var responses = simultaneas(() -> gerarNota(vendaId, "intencao-a"), () -> gerarNota(vendaId, "intencao-b"));
        assertThat(responses.stream().map(MockHttpServletResponse::getStatus)).containsExactlyInAnyOrder(201, 409);
        var criada = responses.stream().filter(response -> response.getStatus() == 201).findFirst().orElseThrow();
        long notaId = JsonPath.<Number>read(criada.getContentAsString(), "$.id").longValue();
        verificarUmaNota(vendaId, notaId);
        verificarUmaConfirmacao(vendaId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mesmaChaveConcorrenteRetornaAMesmaNotaSemDuplicarEfeitos() throws Exception {
        long vendaId = criarVenda();
        assertThat(confirmar(vendaId).getStatus()).isEqualTo(200);
        var responses = simultaneas(() -> gerarNota(vendaId, "mesma-intencao"), () -> gerarNota(vendaId, "mesma-intencao"));
        assertThat(responses.stream().map(MockHttpServletResponse::getStatus)).containsExactly(201, 201);
        long notaId = JsonPath.<Number>read(responses.getFirst().getContentAsString(), "$.id").longValue();
        assertThat(JsonPath.<Number>read(responses.getLast().getContentAsString(), "$.id").longValue()).isEqualTo(notaId);
        verificarUmaNota(vendaId, notaId);
        verificarUmaConfirmacao(vendaId);
    }

    @Test
    void comercialConsultaPedidoSemReceberSecoesFiscalFinanceiraOuEstoque() throws Exception {
        long vendaId = criarVenda();
        assertThat(confirmar(vendaId).getStatus()).isEqualTo(200);
        assertThat(gerarNota(vendaId, "nota-rbac").getStatus()).isEqualTo(201);
        var usuario = tenants.criarUsuario("comercial_venda_" + UUID.randomUUID(), PerfilUsuario.OPERADOR);
        Long membership = tenants.criarMembership(tenant.organizacaoId(), usuario.getId(), tenant.tokenContextual());
        tenants.concederPapel(tenant.organizacaoId(), membership, tenant.tokenContextual(), "COMERCIAL");
        String token = tenants.selecionarOrganizacao(tenant.organizacaoId(), tenants.autenticar(usuario.getLogin(), "senha123"));
        String body = mvc.perform(get("/api/v1/administrative-sales/{id}/documents", vendaId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Object>read(body, "$.nota")).isNull();
        assertThat(JsonPath.<Object>read(body, "$.contas")).isNull();
        assertThat(JsonPath.<Object>read(body, "$.movimentos")).isNull();
        mvc.perform(get("/api/v1/administrative-sales/{id}/documents", vendaId)
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nota.numero").value("NF-CONCORRENCIA"))
                .andExpect(jsonPath("$.contas.length()").value(1))
                .andExpect(jsonPath("$.movimentos.length()").value(1));
        mvc.perform(post("/api/v1/administrative-sales/{id}/outbound-note", vendaId)
                .header(HttpHeaders.AUTHORIZATION, token).header("Idempotency-Key", "sem-fiscal")
                .contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"NF-PROIBIDA\"}"))
                .andExpect(status().isForbidden());
    }

    private long criarVenda() throws Exception {
        String body = mvc.perform(post("/api/v1/administrative-sales")
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual())
                .header("Idempotency-Key", "venda-concorrente")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"VENDA-CONCORRENTE\",\"clienteId\":" + clienteId
                        + ",\"localEstoqueId\":" + localId + ",\"moeda\":\"BRL\",\"itens\":[{\"produtoId\":"
                        + produtoId + ",\"quantidade\":3,\"valorUnitario\":10}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private MockHttpServletResponse confirmar(long vendaId) throws Exception {
        return mvc.perform(post("/api/v1/administrative-sales/{id}/confirmation", vendaId)
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse gerarNota(long vendaId, String chave) throws Exception {
        return mvc.perform(post("/api/v1/administrative-sales/{id}/outbound-note", vendaId)
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual())
                .header("Idempotency-Key", chave).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NF-CONCORRENCIA\"}"))
                .andReturn().getResponse();
    }

    private void verificarUmaConfirmacao(long vendaId) {
        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenant.organizacaoId(), produtoId, localId)).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND origem_tipo='VENDA_ADMINISTRATIVA' AND produto_id=?
                """, Integer.class, tenant.organizacaoId(), produtoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_receber WHERE organizacao_id=? AND venda_administrativa_id=?
                """, Integer.class, tenant.organizacaoId(), vendaId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT sum(valor_total) FROM conta_receber WHERE organizacao_id=? AND venda_administrativa_id=?
                """, BigDecimal.class, tenant.organizacaoId(), vendaId)).isEqualByComparingTo("30");
    }

    private void verificarUmaNota(long vendaId, long notaId) throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM nota_saida WHERE organizacao_id=? AND venda_administrativa_id=?",
                Integer.class, tenant.organizacaoId(), vendaId)).isEqualTo(1);
        mvc.perform(get("/api/v1/administrative-sales/{id}/documents", vendaId)
                .header(HttpHeaders.AUTHORIZATION, tenant.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nota.id").value(notaId));
    }

    private static List<MockHttpServletResponse> simultaneas(
            Callable<MockHttpServletResponse> primeira, Callable<MockHttpServletResponse> segunda) throws Exception {
        var prontas = new CountDownLatch(2);
        var iniciar = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { prontas.countDown(); iniciar.await(); return primeira.call(); });
            var b = pool.submit(() -> { prontas.countDown(); iniciar.await(); return segunda.call(); });
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            iniciar.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
