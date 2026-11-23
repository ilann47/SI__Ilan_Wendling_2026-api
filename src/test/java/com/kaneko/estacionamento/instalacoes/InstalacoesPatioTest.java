package com.kaneko.estacionamento.instalacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.auditoria.RegistroAuditoriaRepository;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class InstalacoesPatioTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistroAuditoriaRepository auditorias;

    private Usuario admin;
    private Usuario operador;
    private Long organizacaoId;
    private Long cidadeId;
    private String tokenAdmin;
    private String tokenContextual;

    @BeforeEach
    void setup() throws Exception {
        admin = usuario("inst_admin", PerfilUsuario.ADMIN);
        operador = usuario("inst_operador", PerfilUsuario.OPERADOR);
        tokenAdmin = bearer(jwt.gerarToken(admin.getLogin(), "OPERADOR"));
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Instalacoes Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(organizacaoId, admin.getId(), tokenAdmin);
        tokenContextual = selecionar(organizacaoId, tokenAdmin);
        cidadeId = criarCidade();
    }

    @Test
    void consultaPaginadaPorNomeRespeitaTenantPermissaoESetorDoPatio() throws Exception {
        var norte = criarPatio(tokenContextual, "Pátio São José", 100, 90, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number patioId = JsonPath.read(norte, "$.id");
        criarPatio(tokenContextual, "Pátio Sul", 100, 90, status().isCreated());
        mvc.perform(post("/api/v1/parking-facilities/{id}/sectors", patioId)
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"S1","name":"Área Coberta","capacity":20,"categories":["COMUM"]}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/parking-facilities").param("name", "sao JOSE").param("size", "1")
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pátio São José"));
        mvc.perform(get("/api/v1/sectors").param("name", "area").param("parkingFacilityId", patioId.toString())
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].parkingFacilityName").value("Pátio São José"));
        mvc.perform(get("/api/v1/sectors").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/installation-cities").param("id", cidadeId.toString())
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(cidadeId));
        mvc.perform(get("/api/v1/parking-facilities").param("size", "101").header("Authorization", tokenContextual))
                .andExpect(status().isBadRequest());

        var outra = organizacoes.criar(new CriarOrganizacaoRequest(
                "11.222.333/0001-81", "Outra instalação Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(outra, admin.getId(), tokenAdmin);
        String outroToken = selecionar(outra, tokenAdmin);
        mvc.perform(get("/api/v1/parking-facilities").param("id", patioId.toString())
                        .param("organizationId", organizacaoId.toString()).header("Authorization", outroToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/sectors").param("parkingFacilityId", patioId.toString())
                        .header("Authorization", outroToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void editaPatioESetorRespeitandoCapacidadesERetornaInativosSomenteNaGestao() throws Exception {
        long patio = ((Number) JsonPath.read(criarPatio(tokenContextual, "Pátio Original", 100, 90,
                status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long setor = ((Number) JsonPath.read(criarSetor(tokenContextual, patio, "A1", "Setor Original", 40,
                "[\"COMUM\"]", status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(post("/api/v1/parking-facilities").header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Pátio Arquivado", 80, 75).replace("true", "false")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/parking-facilities/management").param("name", "arquivado")
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].active").value(false));
        mvc.perform(get("/api/v1/parking-facilities").param("name", "arquivado")
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/v1/parking-facilities/{id}", patio).header("Authorization", tokenContextual)
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Pátio Renomeado", 39, 39)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/parking-facilities/{id}", patio).header("Authorization", tokenContextual)
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Pátio Renomeado", 95, 90)))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));
        mvc.perform(get("/api/v1/sectors/{id}", setor).header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parkingFacilityName").value("Pátio Renomeado"));
        mvc.perform(put("/api/v1/sectors/{id}", setor).header("Authorization", tokenContextual)
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"A2","name":"Setor Renomeado","capacity":42,"categories":["COMUM"]}
                                """))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));
        mvc.perform(get("/api/v1/sectors/management").param("name", "renomeado")
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("Setor Renomeado"));
    }

    @Test
    void criaPatioNoTenantComCapacidadesVersionamentoEAuditoria() throws Exception {
        String body = criarPatio(tokenContextual, "Patio Norte", 100, 90, status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.physicalCapacity").value(100))
                .andExpect(jsonPath("$.operationalCapacity").value(90))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long patioId = ((Number) JsonPath.read(body, "$.id")).longValue();

        var auditoria = auditorias.findAll().stream()
                .filter(a -> "PATIO_CRIADO".equals(a.getAcao()) && patioId == a.getRecursoId())
                .findFirst().orElseThrow();
        assertThat(auditoria.getOrganizacaoId()).isEqualTo(organizacaoId);
        assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        assertThat(auditoria.getEstadoPosterior()).containsEntry("capacidadeFisica", 100);
    }

    @Test
    void exigeContextoPermissaoENaoAceitaTenantDoCliente() throws Exception {
        criarPatio(tokenAdmin, "Patio Sem Contexto", 50, 40, status().isForbidden());

        criarMembership(organizacaoId, operador.getId(), tokenContextual);
        String tokenOperador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(operador.getLogin(), "ADMIN")));
        criarPatio(tokenOperador, "Patio Sem Permissao", 50, 40, status().isForbidden());

        mvc.perform(post("/api/v1/parking-facilities")
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Patio Tenant Forjado", 50, 40)
                                .replace("{", "{\"organizationId\":999999,")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaCapacidadeIncoerenteCidadeInexistenteENomeDuplicado() throws Exception {
        criarPatio(tokenContextual, "Patio Duplicado", 100, 80, status().isCreated());
        criarPatio(tokenContextual, " patio duplicado ", 100, 80, status().isConflict());
        criarPatio(tokenContextual, "Patio Excedido", 100, 101, status().isBadRequest());

        mvc.perform(post("/api/v1/parking-facilities")
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Patio Cidade Invalida", 100, 80)
                                .replace("\"cityId\":" + cidadeId, "\"cityId\":999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiLegadaPermaneceOperanteMasNaoEnxergaPatioTenantAware() throws Exception {
        String target = criarPatio(tokenContextual, "Patio Target", 80, 70, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long targetId = ((Number) JsonPath.read(target, "$.id")).longValue();

        mvc.perform(get("/api/patios/{id}", targetId).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        String legado = mvc.perform(post("/api/patios").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Patio Legado\",\"capacidadeVagas\":25,\"ativo\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long legadoId = ((Number) JsonPath.read(legado, "$.id")).longValue();
        var dados = jdbc.queryForMap(
                "SELECT organizacao_id, capacidade_operacional, version FROM patio WHERE id = ?", legadoId);
        assertThat(dados.get("organizacao_id")).isNull();
        assertThat(((Number) dados.get("capacidade_operacional")).intValue()).isEqualTo(25);
        assertThat(((Number) dados.get("version")).longValue()).isZero();
    }

    @Test
    void criaSetorNoPatioAtivoComCategoriasAuditoriaEOutbox() throws Exception {
        String patio = criarPatio(
                tokenContextual, "Patio Setorizado", 100, 90, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long patioId = ((Number) JsonPath.read(patio, "$.id")).longValue();

        String setor = criarSetor(
                tokenContextual, patioId, " vip-norte ", "Setor VIP Norte", 40,
                "[\"VIP\",\"PCD\"]", status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.parkingFacilityId").value(patioId))
                .andExpect(jsonPath("$.code").value("VIP-NORTE"))
                .andExpect(jsonPath("$.name").value("Setor VIP Norte"))
                .andExpect(jsonPath("$.capacity").value(40))
                .andExpect(jsonPath("$.categories[0]").value("VIP"))
                .andExpect(jsonPath("$.categories[1]").value("PCD"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long setorId = ((Number) JsonPath.read(setor, "$.id")).longValue();

        var auditoria = auditorias.findAll().stream()
                .filter(a -> "SETOR_CRIADO".equals(a.getAcao()) && setorId == a.getRecursoId())
                .findFirst().orElseThrow();
        assertThat(auditoria.getOrganizacaoId()).isEqualTo(organizacaoId);
        assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        assertThat(auditoria.getEstadoPosterior()).containsEntry("codigo", "VIP-NORTE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento_outbox
                WHERE event_type = 'SetorCriado' AND aggregate_type = 'SETOR'
                  AND aggregate_id = ? AND organizacao_id = ?
                """, Integer.class, setorId, organizacaoId)).isEqualTo(1);
    }

    @Test
    void setorExigePermissaoCamposCanonicosEProtegeCapacidadeAgregada() throws Exception {
        String patio = criarPatio(
                tokenContextual, "Patio Limites", 100, 90, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long patioId = ((Number) JsonPath.read(patio, "$.id")).longValue();
        criarSetor(tokenContextual, patioId, "NORTE", "Norte", 60,
                "[\"COMUM\"]", status().isCreated());
        criarSetor(tokenContextual, patioId, " norte ", "Duplicado", 20,
                "[\"COMUM\"]", status().isConflict());
        criarSetor(tokenContextual, patioId, "SUL", "Sul", 41,
                "[\"COMUM\"]", status().isConflict());
        criarSetor(tokenContextual, patioId, "INVALIDO", "Invalido", 10,
                "[\"NAO_EXISTE\"]", status().isBadRequest());

        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Setores Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        criarMembership(outraOrganizacao, admin.getId(), tokenAdmin);
        String tokenOutroTenant = selecionar(outraOrganizacao, tokenAdmin);
        criarSetor(tokenOutroTenant, patioId, "FORA", "Fora", 10,
                "[\"COMUM\"]", status().isNotFound());

        criarMembership(organizacaoId, operador.getId(), tokenContextual);
        String tokenOperador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(operador.getLogin(), "ADMIN")));
        criarSetor(tokenOperador, patioId, "OESTE", "Oeste", 10,
                "[\"COMUM\"]", status().isForbidden());

        mvc.perform(post("/api/v1/parking-facilities/{id}/sectors", patioId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationId":999999,"code":"FORJADO","name":"Forjado",
                                 "capacity":10,"categories":["COMUM"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importaVagasEmLoteComIdempotenciaAuditoriaEOutbox() throws Exception {
        long setorId = criarCenarioSetor("Patio Vagas", 4, "[\"COMUM\",\"PCD\"]");
        String payload = """
                {"spaces":[
                  {"code":" a-001 ","type":"COMUM","accessible":false,"position":"A/01"},
                  {"code":"PCD-01","type":"PCD","accessible":true,"position":"P/01"}
                ]}
                """;

        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectorId").value(setorId))
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.existing").value(0))
                .andExpect(jsonPath("$.conflicts").value(0))
                .andExpect(jsonPath("$.items[0].status").value("CRIADA"))
                .andExpect(jsonPath("$.items[0].code").value("A-001"))
                .andExpect(jsonPath("$.items[1].type").value("PCD"));

        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.existing").value(2))
                .andExpect(jsonPath("$.conflicts").value(0));

        assertThat(auditorias.findAll().stream()
                .filter(a -> "VAGA_CRIADA".equals(a.getAcao())
                        && organizacaoId.equals(a.getOrganizacaoId()) && a.getRecursoId() != null).count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento_outbox
                WHERE event_type = 'VagaCriada' AND aggregate_type = 'VAGA'
                  AND organizacao_id = ?
                """, Integer.class, organizacaoId)).isEqualTo(2);
    }

    @Test
    void importacaoDeVagasRetornaConflitosPontuaisSemExcederSetor() throws Exception {
        long setorId = criarCenarioSetor("Patio Vagas Parciais", 2, "[\"COMUM\"]");
        String inicial = """
                {"spaces":[{"code":"A-01","type":"COMUM",
                  "accessible":false,"position":"A/01"}]}
                """;
        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(inicial))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"spaces":[
                                  {"code":"A-01","type":"COMUM","accessible":true},
                                  {"code":"A-02","type":"COMUM","accessible":false},
                                  {"code":"A-03","type":"COMUM","accessible":false},
                                  {"code":"VIP-01","type":"VIP","accessible":false}
                                ]}
                                """))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.existing").value(0))
                .andExpect(jsonPath("$.conflicts").value(3))
                .andExpect(jsonPath("$.items[0].status").value("CONFLITO"))
                .andExpect(jsonPath("$.items[1].status").value("CRIADA"))
                .andExpect(jsonPath("$.items[2].detail").value(org.hamcrest.Matchers.containsString("capacidade")))
                .andExpect(jsonPath("$.items[3].detail").value(org.hamcrest.Matchers.containsString("categoria")));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM vaga WHERE setor_id = ?", Integer.class, setorId))
                .isEqualTo(2);
    }

    @Test
    void importacaoDeVagasProtegeTenantPermissaoEContrato() throws Exception {
        long setorId = criarCenarioSetor("Patio Vagas Seguro", 2, "[\"COMUM\"]");
        String payload = """
                {"spaces":[{"code":"A-01","type":"COMUM","accessible":false}]}
                """;

        criarMembership(organizacaoId, operador.getId(), tokenContextual);
        String tokenOperador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(operador.getLogin(), "ADMIN")));
        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Vagas Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        criarMembership(outraOrganizacao, admin.getId(), tokenAdmin);
        String tokenOutroTenant = selecionar(outraOrganizacao, tokenAdmin);
        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenOutroTenant)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("{\"spaces\"", "{\"organizationId\":999,\"spaces\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastraUmaVagaDiretamenteECompartilhaAsRegrasComALote() throws Exception {
        long setorId = criarCenarioSetor("Pátio Nova Vaga", 2, "[\"COMUM\",\"PCD\"]");
        String payload = """
                {"code":" p-01 ","type":"PCD","accessible":true,"position":" Junto à rampa "}
                """;
        var resposta = cadastrarVaga(tokenContextual, setorId, payload)
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.code").value("P-01")).andExpect(jsonPath("$.sectorId").value(setorId))
                .andExpect(jsonPath("$.position").value("Junto à rampa")).andExpect(jsonPath("$.status").value("LIVRE"))
                .andReturn().getResponse();
        long id = ((Number) JsonPath.read(resposta.getContentAsString(), "$.id")).longValue();
        assertThat(resposta.getHeader("Location")).endsWith("/api/v1/spaces/" + id);
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual).param("code", "p-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        cadastrarVaga(tokenContextual, setorId, payload).andExpect(status().isConflict());
        cadastrarVaga(tokenContextual, setorId, payload.replace("p-01", "p-02").replace("PCD", "VIP"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId).header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"spaces":[{"code":"P-01","type":"PCD","accessible":true,"position":"Junto à rampa"},
                                           {"code":"P-02","type":"COMUM","accessible":false}]}
                                """))
                .andExpect(status().isMultiStatus()).andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.existing").value(1));
        cadastrarVaga(tokenContextual, setorId, payload.replace("p-01", "p-03")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vaga WHERE setor_id = ?", Long.class, setorId)).isEqualTo(2);
        assertThat(auditorias.findAll().stream().anyMatch(a -> "VAGA_CRIADA".equals(a.getAcao())
                && organizacaoId.equals(a.getOrganizacaoId()) && a.getRecursoId().equals(id))).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento_outbox
                WHERE event_type = 'VagaCriada' AND aggregate_type = 'VAGA' AND aggregate_id = ? AND organizacao_id = ?
                """, Long.class, id, organizacaoId)).isEqualTo(1);
    }

    @Test
    void pesquisaEEditaVagaImportadaSemAlterarSetorOuSituacao() throws Exception {
        long setorId = criarCenarioSetor("Pátio Edição Vagas", 4, "[\"COMUM\",\"PCD\"]");
        long outroSetor = criarCenarioSetor("Pátio Outro Setor", 3, "[\"COMUM\"]");
        long vagaId = importarVagasParaEdicao(setorId);
        importarVagasParaEdicao(outroSetor);
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual).param("code", "a-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual)
                        .param("code", "a-0").param("sectorId", Long.toString(setorId)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].code").value("A-01"))
                .andExpect(jsonPath("$.content[0].parkingFacilityName").value("Pátio Edição Vagas"));
        mvc.perform(get("/api/v1/spaces/{id}", vagaId).header("Authorization", tokenContextual))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.sectorName").value("Setor 1"))
                .andExpect(jsonPath("$.allowedCategories[1]").value("PCD"));
        String alteracao = """
                {"code":" a-09 ","type":"PCD","accessible":true,"position":"  Ao lado da rampa  "}
                """;
        mvc.perform(put("/api/v1/spaces/{id}", vagaId).header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(alteracao))
                .andExpect(status().isPreconditionRequired());
        atualizarVaga(tokenContextual, vagaId, 0, alteracao)
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.code").value("A-09")).andExpect(jsonPath("$.type").value("PCD"))
                .andExpect(jsonPath("$.accessible").value(true))
                .andExpect(jsonPath("$.position").value("Ao lado da rampa"))
                .andExpect(jsonPath("$.sectorId").value(setorId)).andExpect(jsonPath("$.status").value("LIVRE"));
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual)
                        .param("code", "a-09").param("sectorId", Long.toString(setorId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(vagaId));
        assertThat(auditorias.findAll().stream().anyMatch(a -> "VAGA_ATUALIZADA".equals(a.getAcao())
                && organizacaoId.equals(a.getOrganizacaoId()) && a.getRecursoId().equals(vagaId))).isTrue();
        atualizarVaga(tokenContextual, vagaId, 0, alteracao).andExpect(status().isPreconditionFailed());
        atualizarVaga(tokenContextual, vagaId, 1, alteracao.replace("a-09", "a-02")).andExpect(status().isConflict());
        atualizarVaga(tokenContextual, vagaId, 1, alteracao.replace("PCD", "VIP")).andExpect(status().isConflict());
        atualizarVaga(tokenContextual, vagaId, 1, alteracao.replace("\"code\"", "\"sectorId\":9,\"code\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bloqueiaColocaEmManutencaoELiberaVagaComVersaoEAuditoria() throws Exception {
        long setorId = criarCenarioSetor("Pátio Situação Vagas", 2, "[\"COMUM\"]");
        long vagaId = importarVagasParaEdicao(setorId);
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual).param("code", "A-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].version").value(0));
        mvc.perform(patch("/api/v1/spaces/{id}/status", vagaId).header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOQUEADA\"}"))
                .andExpect(status().isPreconditionRequired());
        mudarSituacaoVaga(tokenContextual, vagaId, 0, "BLOQUEADA").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\"")).andExpect(jsonPath("$.status").value("BLOQUEADA"))
                .andExpect(jsonPath("$.sectorId").value(setorId)).andExpect(jsonPath("$.code").value("A-01"));
        mudarSituacaoVaga(tokenContextual, vagaId, 0, "LIVRE").andExpect(status().isPreconditionFailed());
        mudarSituacaoVaga(tokenContextual, vagaId, 1, "MANUTENCAO").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\"")).andExpect(jsonPath("$.status").value("MANUTENCAO"));
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenContextual).param("code", "A-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("MANUTENCAO"))
                .andExpect(jsonPath("$.content[0].version").value(2));
        mudarSituacaoVaga(tokenContextual, vagaId, 2, "LIVRE").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\"")).andExpect(jsonPath("$.status").value("LIVRE"));
        mudarSituacaoVaga(tokenContextual, vagaId, 3, "LIVRE").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));
        mudarSituacaoVaga(tokenContextual, vagaId, 3, "OCUPADA").andExpect(status().isBadRequest());
        assertThat(auditorias.findAll().stream().filter(a -> "VAGA_SITUACAO_ALTERADA".equals(a.getAcao())
                && organizacaoId.equals(a.getOrganizacaoId()) && vagaId == a.getRecursoId()).toList())
                .hasSize(3).anySatisfy(a -> {
                    assertThat(a.getEstadoAnterior()).containsEntry("status", "MANUTENCAO");
                    assertThat(a.getEstadoPosterior()).containsEntry("status", "LIVRE");
                });
    }

    @Test
    void gestaoDeVagasNaoExibeOuAlteraRegistrosDeOutraOrganizacao() throws Exception {
        long setorId = criarCenarioSetor("Pátio Vagas Isoladas", 2, "[\"COMUM\"]");
        long vagaId = importarVagasParaEdicao(setorId);
        criarMembership(organizacaoId, operador.getId(), tokenContextual);
        var tokenOperador = selecionar(organizacaoId, bearer(jwt.gerarToken(operador.getLogin(), "ADMIN")));
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenOperador)).andExpect(status().isForbidden());
        mudarSituacaoVaga(tokenOperador, vagaId, 0, "BLOQUEADA").andExpect(status().isForbidden());
        String novaVaga = """
                {"code":"A-99","type":"COMUM","accessible":false}
                """;
        cadastrarVaga(tokenOperador, setorId, novaVaga).andExpect(status().isForbidden());
        long outroTenant = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Gestão Vagas Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        criarMembership(outroTenant, admin.getId(), tokenAdmin);
        var tokenOutroTenant = selecionar(outroTenant, tokenAdmin);
        mudarSituacaoVaga(tokenOutroTenant, vagaId, 0, "BLOQUEADA").andExpect(status().isNotFound());
        cadastrarVaga(tokenOutroTenant, setorId, novaVaga).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spaces").header("Authorization", tokenOutroTenant)
                        .param("sectorId", Long.toString(setorId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/spaces/{id}", vagaId).header("Authorization", tokenOutroTenant))
                .andExpect(status().isNotFound());
        atualizarVaga(tokenOutroTenant, vagaId, 0, """
                {"code":"A-99","type":"COMUM","accessible":false}
                """).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions mudarSituacaoVaga(String token, long id, long versao, String situacao)
            throws Exception {
        return mvc.perform(patch("/api/v1/spaces/{id}/status", id).header("Authorization", token)
                .header("If-Match", "\"" + versao + "\"").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + situacao + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions cadastrarVaga(String token, long setorId, String payload)
            throws Exception {
        return mvc.perform(post("/api/v1/sectors/{id}/spaces", setorId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private long importarVagasParaEdicao(long setorId) throws Exception {
        var json = mvc.perform(post("/api/v1/sectors/{id}/spaces:batch", setorId)
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spaces":[{"code":"A-01","type":"COMUM","accessible":false},
                                           {"code":"A-02","type":"COMUM","accessible":false}]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.items[0].id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions atualizarVaga(
            String token, long id, long version, String payload) throws Exception {
        return mvc.perform(put("/api/v1/spaces/{id}", id).header("Authorization", token)
                .header("If-Match", "\"" + version + "\"").contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private org.springframework.test.web.servlet.ResultActions criarPatio(
            String token, String nome, int fisica, int operacional,
            org.springframework.test.web.servlet.ResultMatcher status) throws Exception {
        return mvc.perform(post("/api/v1/parking-facilities")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(nome, fisica, operacional)))
                .andExpect(status);
    }

    private String payload(String nome, int fisica, int operacional) {
        return """
                {"name":"%s","address":"Rua das Instalacoes","number":"100",
                 "district":"Centro","postalCode":"80000-000","cityId":%d,
                 "physicalCapacity":%d,"operationalCapacity":%d,"active":true}
                """.formatted(nome, cidadeId, fisica, operacional);
    }

    private org.springframework.test.web.servlet.ResultActions criarSetor(
            String token, long patioId, String codigo, String nome, int capacidade,
            String categorias, org.springframework.test.web.servlet.ResultMatcher status)
            throws Exception {
        return mvc.perform(post("/api/v1/parking-facilities/{id}/sectors", patioId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","capacity":%d,"categories":%s}
                                """.formatted(codigo, nome, capacidade, categorias)))
                .andExpect(status);
    }

    private long criarCenarioSetor(String nomePatio, int capacidadeSetor, String categorias)
            throws Exception {
        String patio = criarPatio(
                tokenContextual, nomePatio, 100, 90, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long patioId = ((Number) JsonPath.read(patio, "$.id")).longValue();
        String setor = criarSetor(tokenContextual, patioId, "SETOR-1", "Setor 1",
                capacidadeSetor, categorias, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(setor, "$.id")).longValue();
    }

    private long criarMembership(long orgId, long userId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", orgId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String selecionar(long orgId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + orgId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    private Long criarCidade() {
        var pais = paises.criar(new PaisRequest("Brasil Instalacoes", "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest("Parana Instalacoes", "PR", pais.getId(), true));
        return cidades.criar(new CidadeRequest("Curitiba Instalacoes", null, null, estado.getId(), true)).getId();
    }

    private Usuario usuario(String login, PerfilUsuario perfil) {
        return usuarios.criar(new UsuarioRequest("Usuario Instalacoes", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
