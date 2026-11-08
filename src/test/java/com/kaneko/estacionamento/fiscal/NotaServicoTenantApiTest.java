package com.kaneko.estacionamento.fiscal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.util.UUID;
import java.time.LocalDate;
import java.math.BigDecimal;
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
    @Autowired ServicoRepository servicos;
    @Autowired CondicaoPagamentoRepository condicoes;
    private TenantFixture fixture;
    private Long clienteId;
    private Long servicoId;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(fixture.organizacaoId());
        cliente.setNome("Cliente servico");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        clienteId = clientes.saveAndFlush(cliente).getId();
        Servico servico = new Servico();
        servico.setOrganizacaoId(fixture.organizacaoId());
        servico.setNome("Lavagem");
        servico.setValorPadrao(BigDecimal.valueOf(50));
        servicoId = servicos.saveAndFlush(servico).getId();
    }

    @Test
    void emiteNotaTenantEGeraContaReceber() throws Exception {
        String body = mvc.perform(post("/api/notas-servico")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NFS-1\",\"clienteId\":" + clienteId
                                + ",\"itens\":[{\"servicoId\":" + servicoId
                                + ",\"quantidade\":2,\"valorUnitario\":50,\"valorDesconto\":5},"
                                + "{\"servicoId\":" + servicoId
                                + ",\"quantidade\":1,\"valorUnitario\":55}],\"aliquotaIss\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizacaoId").value(fixture.organizacaoId()))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].valorTotal").value(95))
                .andExpect(jsonPath("$.parcelas[0].valor").value(150))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(body, "$.id").longValue();
        mvc.perform(post("/api/notas-servico/{id}/emissao", notaId)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("EMITIDA"));
        mvc.perform(get("/api/contas-receber")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].notaServicoId").value(notaId))
                .andExpect(jsonPath("$.content[0].valorTotal").value(150));
        mvc.perform(get("/api/notas-servico/{id}/detalhes", notaId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contasReceber.length()").value(1))
                .andExpect(jsonPath("$.contasReceber[0].valorOriginal").value(150));
        mvc.perform(get("/api/notas-servico").param("numero", "nfs-1").param("situacao", "EMITIDA")
                        .param("clienteId", clienteId.toString())
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/notas-servico").param("numero", "%")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/notas-servico/{id}/cancelamento", notaId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notas-servico/{id}/cancelamento", notaId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Serviço registrado em duplicidade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoCancelamento").value("Serviço registrado em duplicidade"));
        mvc.perform(get("/api/notas-servico/{id}/detalhes", notaId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contasReceber[0].situacao").value("CANCELADA"));
    }

    @Test
    void rejeitaEmissaoFutura() throws Exception {
        mvc.perform(post("/api/notas-servico").header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"FUTURA\",\"clienteId\":" + clienteId
                        + ",\"dataEmissao\":\"" + LocalDate.now().plusDays(1) + "\",\"valorServico\":50}"))
                .andExpect(status().isConflict());
    }

    @Test
    void emiteExatamenteAsParcelasConferidasMesmoAposAlterarCondicao() throws Exception {
        var condicao = new CondicaoPagamento();
        condicao.setOrganizacaoId(fixture.organizacaoId());
        condicao.setNome("Parcelas da nota de serviço");
        condicao.setNumeroParcelas(2);
        for (int i = 1; i <= 2; i++) {
            var p = new ParcelaCondicaoPagamento();
            p.setOrganizacaoId(fixture.organizacaoId());
            p.setCondicaoPagamento(condicao);
            p.setNumero(i);
            p.setDias(i * 10);
            p.setPercentual(BigDecimal.valueOf(50));
            condicao.getParcelas().add(p);
        }
        condicoes.saveAndFlush(condicao);
        String data = LocalDate.now().toString();
        String vencimento = LocalDate.parse(data).plusDays(10).toString();
        String body = """
                {"numero":"NFS-PARC","clienteId":%d,"condicaoPagamentoId":%d,"dataEmissao":"%s",
                 "itens":[{"servicoId":%d,"quantidade":1,"valorUnitario":101.010001}]}
                """.formatted(clienteId, condicao.getId(), data, servicoId);
        String criada = mvc.perform(post("/api/notas-servico").header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelas[0].valor").value(50.505001))
                .andExpect(jsonPath("$.parcelas[1].valor").value(50.505))
                .andExpect(jsonPath("$.parcelas[0].dataVencimento").value(vencimento))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(criada, "$.id").longValue();
        condicao.getParcelas().getFirst().setDias(99);
        condicoes.saveAndFlush(condicao);
        mvc.perform(post("/api/notas-servico/{id}/emissao", id)
                .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notas-servico/{id}/detalhes", id)
                .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contasReceber.length()").value(2))
                .andExpect(jsonPath("$.contasReceber[0].valorOriginal").value(50.505001))
                .andExpect(jsonPath("$.contasReceber[0].dataVencimento").value(vencimento))
                .andExpect(jsonPath("$.contasReceber[1].valorOriginal").value(50.505));
    }
}
