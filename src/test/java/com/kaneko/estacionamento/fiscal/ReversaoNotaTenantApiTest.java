package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TenantApiIntegrationTest
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class ReversaoNotaTenantApiTest {
    private static final String ENTRADAS = "/api/notas-entrada";
    private static final String SAIDAS = "/api/notas-saida";
    private static final String MOTIVO = "{\"motivo\":\"Conferência operacional incorreta\"}";

    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired JdbcTemplate jdbc;
    @Autowired EstoqueService estoque;
    @Autowired FornecedorRepository fornecedores;
    @Autowired ClienteRepository clientes;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;

    private TenantFixture contexto;
    private Long fornecedorId;
    private Long clienteId;
    private Long produtoId;
    private Long localId;

    @BeforeEach
    void criarDados() throws Exception {
        contexto = tenant.criarTenantAdmin();
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setOrganizacaoId(org());
        fornecedor.setNome("Fornecedor reversão");
        fornecedor.setDocumento(documento(14));
        fornecedorId = fornecedores.saveAndFlush(fornecedor).getId();
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(org());
        cliente.setNome("Cliente reversão");
        cliente.setDocumento(documento(11));
        clienteId = clientes.saveAndFlush(cliente).getId();
        Marca marca = new Marca();
        marca.setOrganizacaoId(org());
        marca.setNome("Marca reversão");
        marca = marcas.saveAndFlush(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org());
        unidade.setNome("Unidade reversão");
        unidade.setSigla("REV");
        unidade = unidades.saveAndFlush(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(org());
        produto.setNome("Produto reversão");
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produtoId = produtos.saveAndFlush(produto).getId();
        localId = id(criar(post("/api/v1/stock-locations"), "{\"nome\":\"Armazém\"}"));
        estoque.registrarRecebimento(org(), contexto.usuarioId(), produtoId, localId,
                new BigDecimal("10"), new BigDecimal("20"), "SALDO_INICIAL", "REV:1", "Saldo inicial");
    }

    @Test
    void reverteEntradaComProdutoRepetidoNaOrdemInversaEPreservaHistorico() throws Exception {
        var nota = confirmar(ENTRADAS, id(criar(post(ENTRADAS), entrada())));
        assertSaldoECusto("15", "26.00");

        mvc.perform(reverter(ENTRADAS, nota))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoReversao").value("Conferência operacional incorreta"))
                .andExpect(jsonPath("$.revertidaEm").isNotEmpty());
        assertSaldoECusto("10", "20");
        assertThat(jdbc.queryForObject("select valor_compra_micros * 0.000001 from produto where id=?",
                BigDecimal.class, produtoId)).isEqualByComparingTo("50");
        assertThat(jdbc.queryForList("""
                select situacao from conta_pagar where organizacao_id=? and nota_entrada_id=?
                """, String.class, org(), nota.id())).containsExactly("CANCELADA");
        assertThat(jdbc.queryForList("""
                select original.origem_chave from movimento_estoque reversao
                join movimento_estoque original on original.id=reversao.compensado_id
                where reversao.organizacao_id=? and reversao.origem_tipo='REVERSAO_NOTA_ENTRADA'
                order by reversao.id
                """, String.class, org())).containsExactly(nota.id() + ":2", nota.id() + ":1");
        assertThat(quantidadeCompensacoes()).isEqualTo(2);
        mvc.perform(auth(delete(ENTRADAS + "/{id}", nota.id()))).andExpect(status().isConflict());
    }

    @Test
    void reverteSaidaAvulsaSemAlterarCustoNemRemoverTitulo() throws Exception {
        var nota = confirmar(SAIDAS, id(criar(post(SAIDAS), saida())));
        assertSaldoECusto("8", "20");

        mvc.perform(reverter(SAIDAS, nota)).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
        assertSaldoECusto("10", "20");
        assertThat(jdbc.queryForList("""
                select situacao from conta_receber where organizacao_id=? and nota_saida_id=?
                """, String.class, org(), nota.id())).containsExactly("CANCELADA");
        assertThat(quantidadeCompensacoes()).isEqualTo(1);
    }

    @Test
    void bloqueiaNotaComBaixaParcialSemCriarCompensacao() throws Exception {
        var nota = confirmar(ENTRADAS, id(criar(post(ENTRADAS), entrada())));
        Long titulo = jdbc.queryForObject("select id from conta_pagar where nota_entrada_id=?",
                Long.class, nota.id());
        mvc.perform(json(post("/api/contas-pagar/{id}/baixa", titulo), "{\"valor\":1}"))
                .andExpect(status().isOk());

        mvc.perform(reverter(ENTRADAS, nota)).andExpect(status().isConflict());
        assertSaldoECusto("15", "26");
        assertThat(quantidadeCompensacoes()).isZero();
        assertThat(jdbc.queryForObject("select situacao from nota_entrada where id=?",
                String.class, nota.id())).isEqualTo("CONFIRMADA");
        assertThat(jdbc.queryForObject("select situacao from conta_pagar where id=?",
                String.class, titulo)).isEqualTo("PARCIAL");
    }

    @Test
    void bloqueiaMovimentoPosteriorEPreservaTituloAberto() throws Exception {
        var nota = confirmar(ENTRADAS, id(criar(post(ENTRADAS), entrada())));
        estoque.registrarRecebimento(org(), contexto.usuarioId(), produtoId, localId,
                BigDecimal.ONE, new BigDecimal("26"), "SALDO_INICIAL", "REV:2", "Entrada posterior");

        mvc.perform(reverter(ENTRADAS, nota)).andExpect(status().isConflict());
        assertSaldoECusto("16", "26");
        assertThat(quantidadeCompensacoes()).isZero();
        assertThat(jdbc.queryForObject("select situacao from conta_pagar where nota_entrada_id=?",
                String.class, nota.id())).isEqualTo("PENDENTE");
    }

    @Test
    void exigeVersaoETenantAtivoERecusaVersaoVelha() throws Exception {
        var nota = confirmar(ENTRADAS, id(criar(post(ENTRADAS), entrada())));
        mvc.perform(json(post(ENTRADAS + "/{id}/reversao", nota.id()), MOTIVO))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(json(post(ENTRADAS + "/{id}/reversao", nota.id()), MOTIVO)
                .header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isPreconditionFailed());
        mvc.perform(post(ENTRADAS + "/{id}/reversao", nota.id())
                .header(HttpHeaders.AUTHORIZATION, contexto.tokenGlobal())
                .header(HttpHeaders.IF_MATCH, nota.etag())
                .contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
                .andExpect(status().isForbidden());
        assertThat(quantidadeCompensacoes()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void baixaFiscalRespondeComOrigemSaldoEIdempotenciaSemSessaoNaWeb(boolean pagar) throws Exception {
        var nota = criarNotaFinanceira(pagar);
        long titulo = tituloDaNota(pagar, nota);
        String rota = pagar ? "/api/contas-pagar/{id}" : "/api/contas-receber/{id}";
        String campoValor = pagar ? "valorPago" : "valorRecebido";
        int total = pagar ? 190 : 20;

        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mvc.perform(json(post(rota + "/baixa", titulo), "{\"valor\":5}")
                            .header("Idempotency-Key", "baixa-fiscal-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.situacao").value("PARCIAL"))
                    .andExpect(jsonPath("$." + campoValor).value(5))
                    .andExpect(jsonPath("$.saldo").value(total - 5))
                    .andExpect(jsonPath(pagar ? "$.fornecedorNome" : "$.clienteNome")
                            .value(pagar ? "Fornecedor reversão" : "Cliente reversão"))
                    .andExpect(jsonPath("$.origem.tipo").value(pagar ? "NOTA_ENTRADA" : "NOTA_SAIDA"))
                    .andExpect(jsonPath("$.origem.numero").value(pagar ? "ENT-REV" : "SAI-REV"));
        }
        mvc.perform(json(post(rota + "/baixa", titulo), "{\"valor\":6}")
                        .header("Idempotency-Key", "baixa-fiscal-1"))
                .andExpect(status().isConflict());
        mvc.perform(json(post(rota + "/baixa", titulo), "{\"valor\":" + (total - 5) + "}")
                        .header("Idempotency-Key", "baixa-fiscal-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value(pagar ? "PAGA" : "RECEBIDA"))
                .andExpect(jsonPath("$.saldo").value(0));
        mvc.perform(auth(get(rota + "/baixas", titulo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelamentoDeTituloFiscalRespondeComOrigemSemSessaoNaWeb(boolean pagar) throws Exception {
        var nota = criarNotaFinanceira(pagar);
        long titulo = tituloDaNota(pagar, nota);
        String rota = pagar ? "/api/contas-pagar/{id}/cancelamento" : "/api/contas-receber/{id}/cancelamento";
        mvc.perform(auth(post(rota, titulo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"))
                .andExpect(jsonPath("$.origem.numero").value(pagar ? "ENT-REV" : "SAI-REV"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelamentoDeSaidaRespondeComItensSemSessaoNaWeb() throws Exception {
        long nota = id(criar(post(SAIDAS), saida()));
        mvc.perform(auth(post(SAIDAS + "/{id}/cancelamento", nota))
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"))
                .andExpect(jsonPath("$.clienteNome").value("Cliente reversão"))
                .andExpect(jsonPath("$.itens[0].produtoId").value(produtoId));
    }

    private NotaConfirmada criarNotaFinanceira(boolean pagar) throws Exception {
        String rota = pagar ? ENTRADAS : SAIDAS;
        return confirmar(rota, id(criar(post(rota), pagar ? entrada() : saida())));
    }

    private long tituloDaNota(boolean pagar, NotaConfirmada nota) {
        String sql = pagar ? "select id from conta_pagar where organizacao_id=? and nota_entrada_id=?"
                : "select id from conta_receber where organizacao_id=? and nota_saida_id=?";
        return jdbc.queryForObject(sql, Long.class, org(), nota.id());
    }

    private int quantidadeCompensacoes() {
        return jdbc.queryForObject("select count(*) from movimento_estoque where organizacao_id=? and compensado_id is not null",
                Integer.class, org());
    }

    private void assertSaldoECusto(String saldo, String custo) {
        assertThat(jdbc.queryForObject("select quantidade_micros * 0.000001 from saldo_estoque where organizacao_id=? and produto_id=? and local_estoque_id=?",
                BigDecimal.class, org(), produtoId, localId)).isEqualByComparingTo(saldo);
        assertThat(jdbc.queryForObject("select custo_micros * 0.000001 from produto where id=?",
                BigDecimal.class, produtoId)).isEqualByComparingTo(custo);
    }

    private String entrada() {
        return """
            {"numero":"ENT-REV","fornecedorId":%d,"localEstoqueId":%d,
             "itens":[{"produtoId":%d,"quantidade":4,"valorUnitario":35},
                      {"produtoId":%d,"quantidade":1,"valorUnitario":50}]}
            """.formatted(fornecedorId, localId, produtoId, produtoId);
    }

    private String saida() {
        return """
            {"numero":"SAI-REV","clienteId":%d,"localEstoqueId":%d,
             "itens":[{"produtoId":%d,"quantidade":2,"valorUnitario":10}]}
            """.formatted(clienteId, localId, produtoId);
    }

    private NotaConfirmada confirmar(String rota, long id) throws Exception {
        String resposta = mvc.perform(auth(post(rota + "/{id}/confirmacao", id))
                .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new NotaConfirmada(id, "\"" + JsonPath.read(resposta, "$.version") + "\"");
    }

    private MockHttpServletRequestBuilder reverter(String rota, NotaConfirmada nota) {
        return json(post(rota + "/{id}/reversao", nota.id()), MOTIVO)
                .header(HttpHeaders.IF_MATCH, nota.etag());
    }

    private String criar(MockHttpServletRequestBuilder req, String body) throws Exception {
        return mvc.perform(json(req, body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return auth(req).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, contexto.tokenContextual());
    }

    private static long id(String body) {
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private static String documento(int tamanho) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, tamanho);
    }

    private Long org() {
        return contexto.organizacaoId();
    }

    private record NotaConfirmada(long id, String etag) {}
}
