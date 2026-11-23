package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TenantApiIntegrationTest
class PatioEventoOperacaoApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTestSupport tenants;
    @Autowired JwtService jwt;

    @Test void preparaAvulsoSemProdutosEConciliaPagamentoCorrecaoEFiscal() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        String token = tenant.tokenContextual();
        long org = tenant.organizacaoId();
        tenants.concederPapel(org,tenant.membershipId(),token,"SUPERVISOR");
        long patio = jdbc.queryForObject("""
                INSERT INTO patio(organizacao_id,nome,capacidade_vagas,capacidade_operacional,ativo,version)
                VALUES(?,'Pátio Resumo',20,20,true,0) RETURNING id
                """, Long.class, org);
        long evento = evento(org, patio);
        String base = "/api/v1/events/" + evento;
        mvc.perform(put(base + "/cobranca-patio").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"ENTRADA\",\"valor\":\"25.123456\"}")).andExpect(status().isOk());
        mvc.perform(get(base + "/patio-operation-summary").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preparacao.pronto").value(true))
                .andExpect(jsonPath("$.operacao.abertos").value(0));
        mvc.perform(post(base + "/patio-operation-start").header("Authorization", token)
                .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_OPERACAO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto_estacionamento WHERE evento_id=?", Long.class, evento)).isZero();
        long turno = id(enviar(token, "/api/v1/patio/turnos", "{\"fundoTroco\":\"0\"}").andExpect(status().isCreated()));
        long mov = id(enviar(token, "/api/v1/patio/entradas", """
                {"placa":"QAS1A23","patioId":%d,"eventoId":%d,"chaveEntrada":"%s",
                 "meio":"DINHEIRO","valorEsperado":"25.120000","recebido":"30.000001"}
                """.formatted(patio, evento, UUID.randomUUID())).andExpect(status().isCreated()));
        String rota = "/api/v1/patio/movimentacoes/" + mov;
        String previa = enviar(token, rota + "/previa-saida", "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        enviar(token, rota + "/saida", "{\"turnoId\":" + turno + ",\"chave\":\"" + JsonPath.read(previa, "$.chave")
                + "\",\"meio\":\"SEM_COBRANCA\"}").andExpect(status().isOk());
        mvc.perform(get(base + "/patio-operation-summary").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operacao.abertos").value(0))
                .andExpect(jsonPath("$.operacao.encerrados").value(1))
                .andExpect(jsonPath("$.financeiro.total").value("25.120000"))
                .andExpect(jsonPath("$.fiscal.pendentes").value(1));
        enviar(token, rota + "/correcao", """
                {"chave":"%s","acao":"CORRIGIR_MEIO","meio":"PIX","motivo":"Recebimento realizado por Pix, não dinheiro"}
                """.formatted(UUID.randomUUID())).andExpect(status().isOk());
        mvc.perform(get(base + "/patio-operation-summary").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.financeiro.total").value("25.120000"))
                .andExpect(jsonPath("$.financeiro.dinheiro").value("0.000000"))
                .andExpect(jsonPath("$.financeiro.pix").value("25.120000"));
        enviar(token, rota + "/correcao", """
                {"chave":"%s","acao":"CANCELAR_ESTADIA","motivo":"Estadia registrada em duplicidade e valor devolvido"}
                """.formatted(UUID.randomUUID())).andExpect(status().isOk());
        mvc.perform(get(base + "/patio-operation-summary").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operacao.cancelados").value(1))
                .andExpect(jsonPath("$.financeiro.total").value("0.000000"))
                .andExpect(jsonPath("$.fiscal.pendentes").value(0));
    }

    @Test void isolaOutroTenantERecusaCaixaSemLerEventoSemDeslogar() throws Exception {
        var a = tenants.criarTenantAdmin();
        var b = tenants.criarTenantAdmin();
        long patio = jdbc.queryForObject("""
                INSERT INTO patio(organizacao_id,nome,capacidade_vagas,capacidade_operacional,ativo,version)
                VALUES(?,'Pátio Privado',10,10,true,0) RETURNING id
                """, Long.class, a.organizacaoId());
        long evento = evento(a.organizacaoId(), patio);
        mvc.perform(get("/api/v1/events/" + evento + "/patio-operation-summary").header("Authorization", b.tokenContextual()))
                .andExpect(status().isNotFound());
        var caixa = tenants.criarUsuario("caixa_resumo_" + a.organizacaoId(), PerfilUsuario.OPERADOR);
        long membro = tenants.criarMembership(a.organizacaoId(), caixa.getId(), a.tokenContextual());
        tenants.concederPapel(a.organizacaoId(), membro, a.tokenContextual(), "CAIXA_PATIO");
        String token = tenants.selecionarOrganizacao(a.organizacaoId(), TenantTestSupport.bearer(jwt.gerarToken(caixa)));
        mvc.perform(get("/api/v1/events/" + evento + "/patio-operation-summary").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patio/caixa").header("Authorization", token)).andExpect(status().isOk());
    }

    private long evento(long org, long patio) {
        long pais = jdbc.queryForObject("INSERT INTO pais(nome) VALUES(?) RETURNING id", Long.class, "País Resumo " + org);
        long estado = jdbc.queryForObject("INSERT INTO estado(nome,uf,pais_id) VALUES('Paraná','PR',?) RETURNING id", Long.class, pais);
        long cidade = jdbc.queryForObject("INSERT INTO cidade(nome,estado_id) VALUES('Cidade Resumo',?) RETURNING id", Long.class, estado);
        long local = jdbc.queryForObject("""
                INSERT INTO unidade_operacional(organizacao_id,nome,tipo,endereco,bairro,cep,cidade_id,fuso_horario)
                VALUES(?,'Arena Resumo','VENUE','Rua A','Centro','85000-000',?,'America/Sao_Paulo') RETURNING id
                """, Long.class, org, cidade);
        long evento = jdbc.queryForObject("""
                INSERT INTO evento(organizacao_id,venue_id,nome,inicio,fim,fuso_horario,status,politica_reentrada)
                VALUES(?,?,'Festa avulsa',now()-interval '1 hour',now()+interval '3 hours',
                'America/Sao_Paulo','RASCUNHO','ENTRADA_UNICA') RETURNING id
                """, Long.class, org, local);
        jdbc.update("""
                INSERT INTO alocacao_patio_evento(organizacao_id,evento_id,patio_id,inicio,fim,
                capacidade_operacional,capacidade_vendavel,capacidade_reservada)
                VALUES(?,?,?,now()-interval '1 hour',now()+interval '3 hours',10,0,0)
                """, org, evento, patio);
        return evento;
    }
    private ResultActions enviar(String token, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
