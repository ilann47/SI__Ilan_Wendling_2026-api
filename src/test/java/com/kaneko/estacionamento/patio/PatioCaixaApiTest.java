package com.kaneko.estacionamento.patio;

import static com.kaneko.estacionamento.support.EventoCatalogoFixture.servicoEvento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TenantApiIntegrationTest
class PatioCaixaApiTest {
    private static final String BASE = "/api/v1/patio";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private TenantTestSupport tenants;
    @Autowired private JwtService jwt;

    @Test
    void eventoNaoConfiguradoNaoViraEntradaComum() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        String token = tenant.tokenContextual();
        long patio = criarPatio(tenant.organizacaoId(), 10);
        enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated());
        enviar(token, BASE + "/entradas",
                "{\"placa\":\"EVE1A23\",\"patioId\":" + patio + ",\"eventoId\":99999999}")
                .andExpect(status().isNotFound());
    }

    @Test
    void eventoPagoNaEntradaFechaEmOutroTurnoSemCobrarDuasVezes() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        long org = tenant.organizacaoId();
        String token = tenant.tokenContextual();
        long patio = criarPatio(org, 10);
        long evento = criarEvento(org, patio);
        configurarEvento(token, evento, "ENTRADA");
        long primeiro = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String entrada = entradaEvento(evento, patio, "EVE1A23", UUID.randomUUID());
        enviar(token, BASE + "/entradas", entrada).andExpect(status().isConflict());
        enviar(token, BASE + "/entradas", entrada.replace("}", ",\"meio\":\"DINHEIRO\",\"valorEsperado\":24}"))
                .andExpect(status().isConflict());
        entrada = entrada.replace("}", ",\"meio\":\"DINHEIRO\",\"valorEsperado\":25.120000}");
        long mov = id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated())
                .andExpect(jsonPath("$.evento.valorPago").value("25.120000"))
                .andExpect(jsonPath("$.evento.ordemServicoId").isNumber())
                .andExpect(jsonPath("$.evento.ordemServicoStatus").value("EM_EXECUCAO")));
        long os = jdbc.queryForObject("SELECT id FROM ordem_servico WHERE organizacao_id=? AND movimentacao_patio_id=?",
                Long.class, org, mov);
        assertThat(id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated()))).isEqualTo(mov);
        mvc.perform(get("/api/v1/service-orders/" + os).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.movimentacaoPatioId").value(mov))
                .andExpect(jsonPath("$.itens[0].descricao").value("Estacionamento de evento"));
        String etag = mvc.perform(get("/api/v1/service-orders/" + os).header("Authorization", token))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/v1/service-orders/" + os + "/completion")
                .header("Authorization", token).header("If-Match", etag)).andExpect(status().isConflict());
        enviar(token, BASE + "/entradas",
                entradaEvento(evento, patio, "EVE1A24", UUID.randomUUID())
                        .replace("}", ",\"meio\":\"PIX\",\"valorEsperado\":25.120000}"))
                .andExpect(status().isConflict());
        enviar(token, BASE + "/turnos/" + primeiro + "/fechamento", "{\"dinheiroContado\":25.12}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRecebido").value("25.120000"))
                .andExpect(jsonPath("$.saidas").value(0));
        long segundo = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String pagamento = previaEvento(token, mov, segundo, "0.000000", "SEM_COBRANCA");
        enviar(token, BASE + "/movimentacoes/" + mov + "/saida", pagamento)
                .andExpect(status().isOk()).andExpect(jsonPath("$.evento.fiscalStatus").value("PENDENTE_EMISSAO"))
                .andExpect(jsonPath("$.evento.ordemServicoId").value(os))
                .andExpect(jsonPath("$.evento.ordemServicoStatus").value("CONCLUIDA"));
        enviar(token, BASE + "/movimentacoes/" + mov + "/saida", pagamento).andExpect(status().isOk());
        mvc.perform(get(BASE + "/rascunhos-nfse/" + mov).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tomadorInformado").value(false))
                .andExpect(jsonPath("$.tomador").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE organizacao_id=?", Long.class, org)).isZero();
        mvc.perform(get("/api/v1/service-orders/" + os + "/documents").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nfsePatio.status").value("PENDENTE_EMISSAO"))
                .andExpect(jsonPath("$.nfsePatio.movimentacaoId").value(mov));
        mvc.perform(post("/api/v1/service-orders/" + os + "/service-note").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"INDEVIDA\"}")).andExpect(status().isConflict());
        mvc.perform(get(BASE + "/movimentacoes/" + mov + "/fiscal-evento").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fiscalStatus").value("PENDENTE_EMISSAO"));
        enviar(token, BASE + "/turnos/" + segundo + "/fechamento", "{\"dinheiroContado\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRecebido").value("0.000000"))
                .andExpect(jsonPath("$.saidas").value(1));
        validarFiscalEvento(org, mov);
    }

    @Test
    void eventoPagoNaSaidaMantemPrecoDaEntradaESoContabilizaNoRecebimento() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        long org = tenant.organizacaoId();
        String token = tenant.tokenContextual();
        long patio = criarPatio(org, 10);
        long evento = criarEvento(org, patio);
        configurarEvento(token, evento, "SAIDA");
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        long mov = id(enviar(token, BASE + "/entradas", entradaEvento(evento, patio, "EVE2A23", UUID.randomUUID()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.evento.ordemServicoStatus").value("EM_EXECUCAO")));
        mvc.perform(put("/api/v1/events/" + evento + "/cobranca-patio").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"ENTRADA\",\"valor\":99}"))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/caixa").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aberto.totalRecebido").value("0.000000"));
        String pagamento = previaEvento(token, mov, turno, "25.120000", "PIX");
        enviar(token, BASE + "/movimentacoes/" + mov + "/saida", pagamento).andExpect(status().isOk());
        enviar(token, BASE + "/turnos/" + turno + "/fechamento", "{\"dinheiroContado\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.pix").value("25.120000"));
        validarFiscalEvento(org, mov);
    }

    @Test
    void criaUmRascunhoFiscalComDadosDaEntradaSemReabrirPagamento() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        var externo = tenants.criarTenantAdmin();
        long org = tenant.organizacaoId();
        String token = tenant.tokenContextual();
        jdbc.update("""
                INSERT INTO cliente (organizacao_id,nome,tipo,documento,limite_credito_micros)
                VALUES (?, 'Cliente em outra organização', 'FISICA', '52998224725', 0)
                """, externo.organizacaoId());
        long patio = criarPatio(org, 10);
        long evento = criarEvento(org, patio);
        long servico = servicoEvento(jdbc, org, "Estacionamento no festival", true);
        jdbc.update("UPDATE servico SET valor_padrao_micros=999000000 WHERE id=?", servico);
        jdbc.update("UPDATE evento SET servico_id=? WHERE id=?", servico, evento);
        configurarEvento(token, evento, "ENTRADA");
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String entrada = entradaEvento(evento, patio, "DOC1A23", UUID.randomUUID())
                .replace("}", ",\"meio\":\"PIX\",\"valorEsperado\":25.12,"
                        + "\"tomador\":{\"documento\":\"529.982.247-25\",\"nome\":\" Pessoa Teste \"}}");
        long mov = id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tomador").doesNotExist()));
        Long servicoOs = jdbc.queryForObject("""
                SELECT i.servico_id FROM item_ordem_servico i JOIN ordem_servico os ON os.id=i.ordem_servico_id
                WHERE os.organizacao_id=? AND os.movimentacao_patio_id=?
                """, Long.class, org, mov);
        assertThat(servicoOs).isEqualTo(servico);
        jdbc.update("UPDATE servico SET nome='Nome alterado depois da entrada' WHERE id=?", servico);
        long cliente = jdbc.queryForObject("SELECT id FROM cliente WHERE organizacao_id=? AND documento='52998224725'",
                Long.class, org);
        mvc.perform(get("/api/clientes/" + cliente).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Pessoa Teste"))
                .andExpect(jsonPath("$.tipo").value("FISICA"))
                .andExpect(jsonPath("$.cadastroFiscalBasico").value(true));
        mvc.perform(get("/api/clientes/" + cliente).header("Authorization", externo.tokenContextual()))
                .andExpect(status().isNotFound());
        String lista = BASE + "/rascunhos-nfse";
        String doc = lista + "/" + mov;
        mvc.perform(get(doc).header("Authorization", token)).andExpect(status().isNotFound());
        var saida = BASE + "/movimentacoes/" + mov + "/saida";
        String pagamento = previaEvento(token, mov, turno, "0.000000", "SEM_COBRANCA");
        enviar(token, saida, pagamento).andExpect(status().isOk());
        enviar(token, saida, pagamento).andExpect(status().isOk());
        mvc.perform(get(lista).param("busca", "doc1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movimentacaoId").value(mov))
                .andExpect(jsonPath("$.content[0].tomador").doesNotExist());
        mvc.perform(get(doc).header("Authorization", externo.tokenContextual())).andExpect(status().isNotFound());
        mvc.perform(get(doc).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.valor").value("25.120000"))
                .andExpect(jsonPath("$.ordemServicoId").isNumber())
                .andExpect(jsonPath("$.servicoId").value(servico))
                .andExpect(jsonPath("$.servicoNome").value("Estacionamento no festival"))
                .andExpect(jsonPath("$.tomador.documento").value("52998224725"))
                .andExpect(jsonPath("$.tomador.nome").value("Pessoa Teste"));
        String nomeEmpresa = "E".repeat(160);
        String payload = "{\"tomador\":{\"documento\":\"12.ABC.345/01DE-35\",\"nome\":\"" + nomeEmpresa + "\"}}";
        mvc.perform(put(doc + "/tomador").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isPreconditionRequired());
        mvc.perform(put(doc + "/tomador").header("Authorization", token).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.servicoId").value(servico))
                .andExpect(jsonPath("$.tomador.documento").value("12ABC34501DE35"));
        long empresa = jdbc.queryForObject("SELECT id FROM cliente WHERE organizacao_id=? AND documento='12ABC34501DE35'",
                Long.class, org);
        mvc.perform(get("/api/clientes/" + empresa).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("JURIDICA"))
                .andExpect(jsonPath("$.nome").value(nomeEmpresa))
                .andExpect(jsonPath("$.cadastroFiscalBasico").value(true));
        mvc.perform(put(doc + "/tomador").header("Authorization", token).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tomador\":null}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put(doc + "/tomador").header("Authorization", token).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put(doc + "/tomador").header("Authorization", token).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tomador\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tomador").doesNotExist());
        assertThat(id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated()))).isEqualTo(mov);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_evento_fiscal WHERE organizacao_id=? AND movimentacao_id=?",
                Long.class, org, mov)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM nota_servico WHERE organizacao_id=?", Long.class, org)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE organizacao_id=?", Long.class, org)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT estado_posterior::text FROM registro_auditoria
                WHERE organizacao_id=? AND acao='CLIENTE_CRIADO_PELO_TOMADOR_FISCAL'
                ORDER BY id
                """, String.class, org)).hasSize(2).allSatisfy(snapshot ->
                assertThat(snapshot).contains("RASCUNHO_NFSE_PATIO")
                        .doesNotContain("52998224725", "12ABC34501DE35", "Pessoa Teste", nomeEmpresa));
        validarFiscalEvento(org, mov);
    }

    @Test
    void listaNotasManuaisDeOrdemERascunhoDoPatioComPaginacaoFiltrosEIsolamento() throws Exception {
        var ator = tenants.criarTenantAdmin();
        long org = ator.organizacaoId();
        String token = ator.tokenContextual();
        long patio = criarPatio(org, 10);
        long evento = criarEvento(org, patio);
        configurarEvento(token, evento, "ENTRADA");
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        long mov = id(enviar(token, BASE + "/entradas", entradaEvento(evento, patio, "A%_423", UUID.randomUUID())
                .replace("}", ",\"meio\":\"PIX\",\"valorEsperado\":25.12,"
                        + "\"tomador\":{\"documento\":\"529.982.247-25\",\"nome\":\"Nome Fiscal Privado\"}}"))
                .andExpect(status().isCreated()));
        enviar(token, BASE + "/movimentacoes/" + mov + "/saida",
                previaEvento(token, mov, turno, "0.000000", "SEM_COBRANCA")).andExpect(status().isOk());

        long cliente = jdbc.queryForObject("""
                INSERT INTO cliente (organizacao_id,nome,tipo,documento,limite_credito_micros)
                VALUES (?, 'Cliente da nota manual', 'FISICA', '10000000000', 0) RETURNING id
                """, Long.class, org);
        long ordem = jdbc.queryForObject("""
                INSERT INTO ordem_servico(organizacao_id,cliente_id,numero,data_abertura,status,
                    iniciada_em,concluida_em,subtotal_micros,valor_total_micros)
                VALUES (?, ?, 'ORDEM-QA', current_date,'CONCLUIDA',now(),now(),12345678,12345678) RETURNING id
                """, Long.class, org, cliente);
        long notaAvulsa = jdbc.queryForObject("""
                INSERT INTO nota_servico(organizacao_id,cliente_id,numero,created_at,situacao,valor_total_micros)
                VALUES (?,?, 'NOTA-MANUAL', now()-interval '1 day','EMITIDA',30123456) RETURNING id
                """, Long.class, org, cliente);
        long notaOrdem = jdbc.queryForObject("""
                INSERT INTO nota_servico(organizacao_id,cliente_id,ordem_servico_id,numero,created_at,valor_total_micros)
                VALUES (?,?,?, 'NOTA-ORDEM',now()-interval '2 days',12345678) RETURNING id
                """, Long.class, org, cliente, ordem);

        String notas = "/api/notas-servico/listagem";
        mvc.perform(get(notas).param("size","2").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(mov))
                .andExpect(jsonPath("$.content[0].origem").value("ESTACIONAMENTO"))
                .andExpect(jsonPath("$.content[0].referencia").isNotEmpty())
                .andExpect(jsonPath("$.content[0].descricao").value("A%_423 · Evento no pátio · Pátio 10"))
                .andExpect(jsonPath("$.content[0].situacao").value("PENDENTE_EMISSAO"))
                .andExpect(jsonPath("$.content[0].valor").value("25.120000"))
                .andExpect(jsonPath("$.content[0].tomador").doesNotExist())
                .andExpect(jsonPath("$.content[1].id").value(notaAvulsa))
                .andExpect(jsonPath("$.content[1].origem").value("MANUAL"))
                .andExpect(jsonPath("$.content[1].valor").value("30.123456"));
        mvc.perform(get(notas).param("size","2").param("page","1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(notaOrdem))
                .andExpect(jsonPath("$.content[0].origem").value("ORDEM_SERVICO"));
        mvc.perform(get(notas).param("situacao","PENDENTE").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(notas).param("origem","MANUAL").param("situacao","EMITIDA")
                .param("clienteId",String.valueOf(cliente)).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].referencia").value("NOTA-MANUAL"));
        for (String busca : new String[]{"%_","evento","pátio 10","ordem-qa","nota-manual"}) {
            mvc.perform(get(notas).param("busca",busca).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(get(notas).param("busca","Nome Fiscal Privado").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(notas).param("size","51").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get(notas).param("page","-1").header("Authorization", token))
                .andExpect(status().isBadRequest());
        var externo = tenants.criarTenantAdmin();
        mvc.perform(get(notas).header("Authorization", externo.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE organizacao_id=?", Long.class, org)).isZero();
    }

    @Test
    void recebeTomadorNaSaidaECriaClienteUmaVezSomenteComDadosValidos() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        long org = tenant.organizacaoId();
        String token = tenant.tokenContextual();
        long patio = criarPatio(org, 10);
        long evento = criarEvento(org, patio);
        configurarEvento(token, evento, "SAIDA");
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String entrada = entradaEvento(evento, patio, "DOC1A24", UUID.randomUUID());
        String tomador = ",\"tomador\":{\"documento\":\"529.982.247-25\",\"nome\":\"Pessoa Teste\"}}";
        enviar(token, BASE + "/entradas", entrada.replace("}", tomador)).andExpect(status().isBadRequest());
        long mov = id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated()));
        String pagamento = previaEvento(token, mov, turno, "25.120000", "PIX");
        String saida = BASE + "/movimentacoes/" + mov + "/saida";
        enviar(token, saida, pagamento.replace("}", tomador.replace("529.982.247-25", "111.111.111-11")))
                .andExpect(status().isBadRequest());
        var original = jdbc.queryForObject("SELECT count(*) FROM cliente WHERE organizacao_id=?", Long.class, org);
        enviar(token, saida, pagamento.replace("}", tomador)).andExpect(status().isOk());
        enviar(token, saida, pagamento.replace("}", tomador)).andExpect(status().isOk());
        enviar(token, saida, pagamento.replace("}", tomador.replace("Pessoa Teste", "Outro Nome")))
                .andExpect(status().isConflict());
        assertThat(id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated()))).isEqualTo(mov);
        mvc.perform(get(BASE + "/rascunhos-nfse/" + mov).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tomador.documento").value("52998224725"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE organizacao_id=?", Long.class, org)).isEqualTo(original + 1);
        assertThat(jdbc.queryForObject("SELECT nome FROM cliente WHERE organizacao_id=? AND documento='52998224725'",
                String.class, org)).isEqualTo("Pessoa Teste");
        validarFiscalEvento(org, mov);
    }

    @Test
    void tomadorReutilizaDocumentoDoTenantSemSobrescreverCadastroInativoNemVincularPlaca() throws Exception {
        var tenant = tenants.criarTenantAdmin();
        var externo = tenants.criarTenantAdmin();
        long org = tenant.organizacaoId();
        String token = tenant.tokenContextual();
        jdbc.update("""
                INSERT INTO cliente (organizacao_id,nome,tipo,documento,ativo,email,limite_credito_micros)
                VALUES (?, 'Cadastro inativo', 'FISICA', '529.982.247-25', false, 'original@example.test', 0),
                       (?, 'Cadastro externo', 'FISICA', '52998224725', true, null, 0)
                """, org, externo.organizacaoId());
        long patio = criarPatio(org, 10);
        long evento = criarEvento(org, patio);
        configurarEvento(token, evento, "ENTRADA");
        id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String entrada = entradaEvento(evento, patio, "DOC1A25", UUID.randomUUID())
                .replace("}", ",\"meio\":\"PIX\",\"valorEsperado\":25.12,"
                        + "\"tomador\":{\"documento\":\"52998224725\",\"nome\":\"Nome da nota\"}}");
        enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated());
        assertThat(jdbc.queryForList("SELECT nome, ativo, email FROM cliente WHERE organizacao_id=?", org))
                .singleElement().satisfies(cadastro -> assertThat(cadastro)
                        .containsEntry("nome", "Cadastro inativo").containsEntry("ativo", false)
                        .containsEntry("email", "original@example.test"));
        assertThat(jdbc.queryForObject("SELECT nome FROM cliente WHERE organizacao_id=?", String.class, externo.organizacaoId()))
                .isEqualTo("Cadastro externo");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veiculo WHERE organizacao_id=? AND cliente_id IS NOT NULL",
                Long.class, org)).isZero();
    }

    @Test
    void dinheiroTrocoReciboCorrecaoECancelamentoPreservamFontes() throws Exception {
        var tenant=tenants.criarTenantAdmin();
        long org=tenant.organizacaoId();
        String token=tenant.tokenContextual();
        long patio=criarPatio(org,10),evento=criarEvento(org,patio);
        configurarEvento(token,evento,"ENTRADA");
        long turno=id(enviar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String entrada=entradaEvento(evento,patio,"QAT1A23",UUID.randomUUID())
            .replace("}",",\"meio\":\"DINHEIRO\",\"valorEsperado\":25.120000,\"recebido\":30}");
        long mov=id(enviar(token,BASE+"/entradas",entrada).andExpect(status().isCreated())
            .andExpect(jsonPath("$.troco").value("4.880000")));
        enviar(token,BASE+"/entradas",entrada).andExpect(status().isCreated());
        mvc.perform(get(BASE+"/movimentacoes/"+mov+"/comprovante").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value("25.120000"))
            .andExpect(jsonPath("$.recebido").value("30.000000")).andExpect(jsonPath("$.troco").value("4.880000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_pagamento_dinheiro WHERE movimentacao_id=?",Long.class,mov)).isEqualTo(1);
        String corregir="{\"chave\":\""+UUID.randomUUID()+"\",\"acao\":\"CORRIGIR_MEIO\",\"meio\":\"PIX\",\"motivo\":\"Meio informado incorretamente\"}";
        enviar(token,BASE+"/movimentacoes/"+mov+"/correcao",corregir).andExpect(status().isOk());
        enviar(token,BASE+"/movimentacoes/"+mov+"/correcao",corregir).andExpect(status().isOk());
        mvc.perform(get(BASE+"/caixa").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.aberto.dinheiro").value("0.000000")).andExpect(jsonPath("$.aberto.pix").value("25.120000"));
        assertThat(jdbc.queryForObject("SELECT meio_entrada FROM patio_evento_estadia WHERE movimentacao_id=?",String.class,mov)).isEqualTo("DINHEIRO");
        String saida=previaEvento(token,mov,turno,"0.000000","SEM_COBRANCA");
        enviar(token,BASE+"/movimentacoes/"+mov+"/saida",saida).andExpect(status().isOk());
        enviar(token,BASE+"/movimentacoes/"+mov+"/saida",saida).andExpect(status().isOk())
            .andExpect(jsonPath("$.recebido").doesNotExist()).andExpect(jsonPath("$.troco").doesNotExist());
        String cancelamento="{\"chave\":\""+UUID.randomUUID()+"\",\"acao\":\"CANCELAR_ESTADIA\",\"motivo\":\"Cobrança devolvida ao cliente\"}";
        enviar(token,BASE+"/movimentacoes/"+mov+"/correcao",cancelamento).andExpect(status().isOk());
        enviar(token,BASE+"/movimentacoes/"+mov+"/correcao",cancelamento).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_operacao_ajuste WHERE movimentacao_id=?",Long.class,mov)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM ordem_servico WHERE movimentacao_patio_id=?",String.class,mov)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT status FROM patio_evento_fiscal WHERE movimentacao_id=?",String.class,mov)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT valor_micros FROM patio_evento_fiscal WHERE movimentacao_id=?",BigDecimal.class,mov)).isEqualByComparingTo("25120000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE organizacao_id=?",Long.class,org)).isZero();
        mvc.perform(get(BASE+"/comprovantes").param("placa","QAT1A23").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].cancelada").value(true));
        enviar(token,BASE+"/turnos/"+turno+"/fechamento","{\"dinheiroContado\":0}").andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRecebido").value("0.000000"));
    }

    @Test
    void corrigePlacaSomenteDaEstadiaERejeitaOutroTenant() throws Exception {
        var a=tenants.criarTenantAdmin(); var b=tenants.criarTenantAdmin();
        String token=a.tokenContextual(); long org=a.organizacaoId();
        long patio=criarPatio(org,10),evento=criarEvento(org,patio);
        configurarEvento(token,evento,"SAIDA");
        enviar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated());
        long mov=id(enviar(token,BASE+"/entradas",entradaEvento(evento,patio,"QAT2A23",UUID.randomUUID())).andExpect(status().isCreated()));
        String body="{\"chave\":\""+UUID.randomUUID()+"\",\"acao\":\"CORRIGIR_PLACA\",\"placa\":\"QAT2A24\",\"motivo\":\"Placa digitada com erro na entrada\"}";
        enviar(b.tokenContextual(),BASE+"/movimentacoes/"+mov+"/correcao",body).andExpect(status().isConflict());
        enviar(token,BASE+"/movimentacoes/"+mov+"/correcao",body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veiculo WHERE organizacao_id=? AND placa='QAT2A23'",Long.class,org)).isEqualTo(1);
        mvc.perform(get(BASE+"/busca-saida").param("placa","QAT2").param("patioId",String.valueOf(patio))
            .header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].placa").value("QAT2A24"));
        mvc.perform(get(BASE+"/busca-saida").param("placa","QAT2").header("Authorization",b.tokenContextual()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE+"/movimentacoes/"+mov+"/comprovante").header("Authorization",b.tokenContextual()))
            .andExpect(status().isNotFound());
    }

    @Test
    void cancelarEntradaPagaLiberaOcupacaoEConservaRecebimento() throws Exception {
        var a=tenants.criarTenantAdmin(); String token=a.tokenContextual(); long org=a.organizacaoId();
        long patio=criarPatio(org,10),evento=criarEvento(org,patio); configurarEvento(token,evento,"ENTRADA");
        enviar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated());
        String entrada=entradaEvento(evento,patio,"QAT3A23",UUID.randomUUID())
            .replace("}",",\"meio\":\"DINHEIRO\",\"valorEsperado\":25.120000,\"recebido\":20}");
        enviar(token,BASE+"/entradas",entrada).andExpect(status().isConflict());
        long mov=id(enviar(token,BASE+"/entradas",entrada.replace("\"recebido\":20","\"recebido\":30")).andExpect(status().isCreated()));
        String cancelamento="{\"chave\":\""+UUID.randomUUID()+"\",\"acao\":\"CANCELAR_ESTADIA\",\"motivo\":\"Entrada lançada por engano, valor devolvido\"}";
        enviar(token,BASE+"/movimentacoes/"+mov+"/correcao",cancelamento).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ocupacao_atual FROM alocacao_patio_evento WHERE evento_id=?",Integer.class,evento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_evento_estadia WHERE movimentacao_id=?",Long.class,mov)).isEqualTo(1);
        mvc.perform(get(BASE+"/caixa").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.aberto.totalRecebido").value("0.000000"));
    }

    private long criarEvento(long org, long patio) {
        long pais = jdbc.queryForObject("INSERT INTO pais(nome) VALUES(?) RETURNING id",
                Long.class, "País QA " + org);
        long estado = jdbc.queryForObject("INSERT INTO estado(nome,uf,pais_id) VALUES('Paraná','PR',?) RETURNING id",
                Long.class, pais);
        long cidade = jdbc.queryForObject("INSERT INTO cidade(nome,estado_id) VALUES('Cidade QA',?) RETURNING id",
                Long.class, estado);
        long local = jdbc.queryForObject("""
                INSERT INTO unidade_operacional (organizacao_id,nome,tipo,endereco,bairro,cep,cidade_id,fuso_horario)
                VALUES (?, 'Arena', 'VENUE', 'Rua Arena', 'Centro', '01001-000', ?, 'America/Sao_Paulo') RETURNING id
                """, Long.class, org, cidade);
        long evento = jdbc.queryForObject("""
                INSERT INTO evento (organizacao_id,venue_id,nome,inicio,fim,fuso_horario,status,politica_reentrada)
                VALUES (?, ?, 'Evento no pátio',now()-interval '1 hour',now()+interval '3 hours',
                'America/Sao_Paulo','EM_OPERACAO','ENTRADA_UNICA') RETURNING id
                """, Long.class, org, local);
        jdbc.update("""
                INSERT INTO alocacao_patio_evento (organizacao_id,evento_id,patio_id,inicio,fim,
                capacidade_operacional,capacidade_vendavel,capacidade_reservada)
                VALUES (?,?,?,now()-interval '1 hour',now()+interval '3 hours',10,8,1)
                """, org, evento, patio);
        return evento;
    }

    private void configurarEvento(String token, long evento, String modo) throws Exception {
        mvc.perform(put("/api/v1/events/" + evento + "/cobranca-patio").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"modo":"%s","valor":"25.123456"}
                """.formatted(modo))).andExpect(status().isOk());
    }

    private static String entradaEvento(long evento, long patio, String placa, UUID chave) {
        return """
                {"placa":"%s","eventoId":%d,"patioId":%d,"chaveEntrada":"%s"}
                """.formatted(placa, evento, patio, chave).strip();
    }

    private String previaEvento(String token, long mov, long turno, String valor, String meio) throws Exception {
        String previa = enviar(token, BASE + "/movimentacoes/" + mov + "/previa-saida", "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(valor))
                .andReturn().getResponse().getContentAsString();
        return "{\"turnoId\":%d,\"chave\":\"%s\",\"meio\":\"%s\"}"
                .formatted(turno, (String) JsonPath.read(previa, "$.chave"), meio);
    }

    private void validarFiscalEvento(long org, long mov) {
        assertThat(jdbc.queryForObject("""
                SELECT f.valor_micros FROM patio_evento_fiscal f
                JOIN ordem_servico os ON os.organizacao_id=f.organizacao_id AND os.id=f.ordem_servico_id
                  AND os.movimentacao_patio_id=f.movimentacao_id AND os.valor_total_micros=f.valor_micros
                WHERE f.organizacao_id=? AND f.movimentacao_id=? AND f.status='PENDENTE_EMISSAO'
                  AND os.status='CONCLUIDA'
                """, BigDecimal.class, org, mov)).isEqualByComparingTo("25120000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servico WHERE organizacao_id=?", Long.class, org)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE organizacao_id=?", Long.class, org)).isZero();
    }

    @Test
    void aceitaIdentificadoresForaDoPadraoEEncontraNaSaidaEComprovante() throws Exception {
        var ator = tenants.criarTenantAdmin();
        String token = ator.tokenContextual();
        criarPatio(ator.organizacaoId(), 10);
        enviar(token, "/api/tarifas", """
                {"descricao":"Avulso","valorHora":10,"valorDiaria":80,"valorMensalidade":150}
                """).andExpect(status().isCreated());
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}")
                .andExpect(status().isCreated()));
        for (String placa : new String[]{"HBF593", "X", "XYZZ"}) {
            enviar(token, BASE + "/entradas", "{\"placa\":\"" + placa + "\"}")
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.placa").value(placa));
        }
        var entrada = enviar(token, BASE + "/entradas", """
                {"placa":"x%_ -7"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.placa").value("X%_ -7"));
        long movimento = id(entrada);
        enviar(token, BASE + "/entradas", "{\"placa\":\"X%_ -7\"}").andExpect(status().isConflict());
        enviar(token, BASE + "/entradas", "{\"placa\":\"12345678901\"}").andExpect(status().isBadRequest());
        enviar(token, BASE + "/entradas", "{\"placa\":\"X\\u0000\"}").andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/busca-saida").param("placa", "%_").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].movimentacaoId").value(movimento));
        mvc.perform(get(BASE + "/busca-saida").param("placa", "x").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].placa").value("X"));
        jdbc.update("UPDATE movimentacao SET data_entrada=now()-interval '20 minutes' WHERE id=?", movimento);
        em.clear();
        var rota = BASE + "/movimentacoes/" + movimento;
        String previa = enviar(token, rota + "/previa-saida", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("10.000000"))
                .andReturn().getResponse().getContentAsString();
        String chave = JsonPath.read(previa, "$.chave");
        enviar(token, rota + "/saida", """
                {"turnoId":%d,"chave":"%s","meio":"PIX"}
                """.formatted(turno, chave)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/comprovantes").param("placa", "%_").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placa").value("X%_ -7"));
    }

    @Test
    void operaDaPlacaAoFechamentoComSeisCasasSemGerarOrdemOuRecebivel() throws Exception {
        var a = tenants.criarTenantAdmin();
        var b = tenants.criarTenantAdmin();
        String token = a.tokenContextual();
        var entrada = "{\"placa\":\"abc-1d23\"}";
        enviar(token, BASE + "/entradas", entrada).andExpect(status().isConflict());
        enviar(token, "/api/tarifas", """
                {"descricao":"Avulso","valorHora":12.345678,"valorDiaria":80,"valorMensalidade":150}
                """).andExpect(status().isCreated());
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":\"10.000001\"}")
                .andExpect(status().isCreated()));
        enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isConflict());
        enviar(token, BASE + "/entradas", "{\"placa\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.placa").isNotEmpty());
        enviar(token, BASE + "/entradas", entrada).andExpect(status().isConflict());
        criarPatio(a.organizacaoId(), 10);
        long movimento = id(enviar(token, BASE + "/entradas", entrada).andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1D23")));
        enviar(token, BASE + "/entradas", entrada).andExpect(status().isConflict());
        enviar(token, "/api/movimentacoes/" + movimento + "/saida", "{}").andExpect(status().isConflict());
        jdbc.update("UPDATE movimentacao SET data_entrada=now()-interval '20 minutes' WHERE id=?", movimento);
        em.clear();
        var rota = BASE + "/movimentacoes/" + movimento;
        enviar(b.tokenContextual(), BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated());
        enviar(b.tokenContextual(), rota + "/previa-saida", "{}").andExpect(status().isNotFound());
        String previa = enviar(token, rota + "/previa-saida", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("12.350000"))
                .andReturn().getResponse().getContentAsString();
        var chave = (String) JsonPath.read(previa, "$.chave");
        var pagamento = """
                {"turnoId":%d,"chave":"%s","meio":"DINHEIRO"}
                """.formatted(turno, chave);
        enviar(token, rota + "/saida", pagamento.replace("DINHEIRO", "SEM_COBRANCA"))
                .andExpect(status().isConflict());
        enviar(token, rota + "/saida", pagamento).andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("12.350000"));
        enviar(token, rota + "/saida", pagamento).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT valor_micros FROM patio_checkout WHERE movimentacao_id=?",
                BigDecimal.class, movimento)).isEqualByComparingTo("12350000");

        long aindaNoPatio = id(enviar(token, BASE + "/entradas", "{\"placa\":\"ZZZ0001\"}")
                .andExpect(status().isCreated()));
        String fechamento = BASE + "/turnos/" + turno + "/fechamento";
        enviar(b.tokenContextual(), fechamento, "{\"dinheiroContado\":22.350001}")
                .andExpect(status().isNotFound());
        enviar(token, fechamento, "{\"dinheiroContado\":22.350000}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.dinheiroEsperado").value("22.350001"))
                .andExpect(jsonPath("$.diferencaDinheiro").value("-0.000001"))
                .andExpect(jsonPath("$.saidas").value(1));
        assertThat(jdbc.queryForObject("SELECT status FROM movimentacao WHERE id=?", String.class, aindaNoPatio))
                .isEqualTo("ABERTO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servico WHERE organizacao_id=?",
                Long.class, a.organizacaoId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE organizacao_id=?",
                Long.class, a.organizacaoId())).isZero();
        mvc.perform(get(BASE + "/caixa").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoFechado.totalRecebido").value("12.350000"));
    }

    @Test
    void mensalistaPassaParaProximoTurnoESaiSemPagamento() throws Exception {
        var a = tenants.criarTenantAdmin();
        String token = a.tokenContextual();
        criarPatio(a.organizacaoId(), 10);
        long cliente = id(enviar(token, "/api/clientes", "{\"nome\":\"Cliente\",\"documento\":\"11111111111\"}")
                .andExpect(status().isCreated()));
        long veiculo = id(enviar(token, "/api/veiculos", "{\"placa\":\"XYZ-1A23\",\"clienteId\":" + cliente + "}")
                .andExpect(status().isCreated()));
        enviar(token, "/api/mensalistas", """
                {"clienteId":%d,"veiculoId":%d,"valorMensal":100}
                """.formatted(cliente, veiculo)).andExpect(status().isCreated());
        long primeiro = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        long movimento = id(enviar(token, BASE + "/entradas", "{\"placa\":\"XYZ1A23\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("MENSALISTA")));
        String rota = BASE + "/movimentacoes/" + movimento;
        enviar(token, BASE + "/turnos/" + primeiro + "/fechamento", "{\"dinheiroContado\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.saidas").value(0));
        long segundo = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String json = enviar(token, rota + "/previa-saida", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("0.000000")).andReturn().getResponse().getContentAsString();
        String pagamento = """
                {"turnoId":%d,"chave":"%s","meio":"SEM_COBRANCA"}
                """.formatted(segundo, (String) JsonPath.read(json, "$.chave"));
        enviar(token, rota + "/saida", pagamento.replace("SEM_COBRANCA", "PIX")).andExpect(status().isConflict());
        enviar(token, rota + "/saida", pagamento).andExpect(status().isOk())
                .andExpect(jsonPath("$.meio").value("SEM_COBRANCA"));
        enviar(token, BASE + "/turnos/" + segundo + "/fechamento", "{\"dinheiroContado\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.saidas").value(1))
                .andExpect(jsonPath("$.totalRecebido").value("0.000000"));
    }

    @Test
    void exigePatioQuandoHaMaisDeUmEImpedeLotacaoExcedida() throws Exception {
        var a = tenants.criarTenantAdmin();
        var b = tenants.criarTenantAdmin();
        String token = a.tokenContextual();
        long primeiro = criarPatio(a.organizacaoId(), 1);
        criarPatio(a.organizacaoId(), 10);
        long outroTenant = criarPatio(b.organizacaoId(), 10);
        enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated());
        enviar(token, "/api/tarifas", """
                {"descricao":"Avulso","valorHora":10,"valorDiaria":80,"valorMensalidade":150}
                """).andExpect(status().isCreated());
        enviar(token, BASE + "/entradas", "{\"placa\":\"ABC1234\"}").andExpect(status().isConflict());
        enviar(token, BASE + "/entradas", "{\"placa\":\"ABC1234\",\"patioId\":" + outroTenant + "}")
                .andExpect(status().isNotFound());
        enviar(token, BASE + "/entradas", "{\"placa\":\"ABC1234\",\"patioId\":" + primeiro + "}")
                .andExpect(status().isCreated());
        enviar(token, BASE + "/entradas", "{\"placa\":\"XYZ1234\",\"patioId\":" + primeiro + "}")
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veiculo WHERE organizacao_id=?",
                Long.class, a.organizacaoId())).isEqualTo(1);
    }

    private long criarPatio(long org, int capacidade) {
        return jdbc.queryForObject("""
                INSERT INTO patio(organizacao_id,nome,capacidade_vagas,capacidade_operacional,
                 ativo,version,created_at,updated_at) VALUES(?, ?, ?, ?,true,0,now(),now()) RETURNING id
                """, Long.class, org, "Pátio " + capacidade, capacidade, capacidade);
    }

    @Test
    void papelDeCaixaOperaSemAdministrarTarifasOuAcessarTurnoAlheio() throws Exception {
        var admin = tenants.criarTenantAdmin();
        var operador = tenants.criarUsuario("caixa_" + admin.organizacaoId(), PerfilUsuario.OPERADOR);
        long vinculo = tenants.criarMembership(admin.organizacaoId(), operador.getId(), admin.tokenContextual());
        String token = tenants.selecionarOrganizacao(admin.organizacaoId(),
                TenantTestSupport.bearer(jwt.gerarToken(operador)));
        enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isForbidden());
        tenants.concederPapel(admin.organizacaoId(), vinculo, admin.tokenContextual(), "CAIXA_PATIO");
        long turno = id(enviar(token, BASE + "/turnos", "{\"fundoTroco\":0}").andExpect(status().isCreated()));
        enviar(token,BASE+"/movimentacoes/99999999/correcao",
            "{\"chave\":\""+UUID.randomUUID()+"\",\"acao\":\"CANCELAR_ESTADIA\",\"motivo\":\"Operador não pode cancelar\"}")
            .andExpect(status().isForbidden());
        mvc.perform(get(BASE+"/busca-saida").param("placa","QAT").header("Authorization",token))
            .andExpect(status().isOk());
        mvc.perform(get(BASE + "/rascunhos-nfse").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notas-servico/listagem").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/rascunhos-nfse/1/tomador").header("Authorization", token).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tomador\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/relatorios/patio").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/tarifas").header("Authorization", token)).andExpect(status().isForbidden());
        enviar(admin.tokenContextual(), BASE + "/turnos/" + turno + "/fechamento", "{\"dinheiroContado\":0}")
                .andExpect(status().isNotFound());
        enviar(token, BASE + "/turnos/" + turno + "/fechamento", "{\"dinheiroContado\":0}")
                .andExpect(status().isOk());
    }

    private ResultActions enviar(String token, String rota, String corpo) throws Exception {
        return mvc.perform(post(rota).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static long id(ResultActions action) throws Exception {
        return ((Number) JsonPath.read(action.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
