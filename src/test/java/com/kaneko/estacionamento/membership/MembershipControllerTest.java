package com.kaneko.estacionamento.membership;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MembershipControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrganizacaoService organizacaoService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private OrganizacaoRepository organizacaoRepository;

    @Test
    void criaConsultaSuspendeReativaEEncerraComConcorrenciaOtimista() throws Exception {
        Long organizacaoId = criarOrganizacao();
        Long usuarioId = criarUsuario("membro_api").getId();

        String criacao = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.userId").value(usuarioId))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.origin").value("ADMINISTRATIVA"))
                .andReturn().getResponse().getContentAsString();
        Number membershipId = JsonPath.read(criacao, "$.id");

        mvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId.longValue()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""));

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId.longValue())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENSO\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("SUSPENSO"));

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId.longValue())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATIVO\"}"))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId.longValue())
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""));

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId.longValue())
                        .header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENCERRADO\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.status").value("ENCERRADO"));
    }

    @Test
    void exigeIfMatchERejeitaCamposControladosPeloServidor() throws Exception {
        Long organizacaoId = criarOrganizacao();
        Long usuarioId = criarUsuario("membro_patch").getId();
        long membershipId = criarMembership(organizacaoId, usuarioId);

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENSO\"}"))
                .andExpect(status().isPreconditionRequired());

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENSO\",\"userId\":999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bloqueiaDuplicidadeEPermiteNovoVinculoDepoisDoEncerramento() throws Exception {
        Long organizacaoId = criarOrganizacao();
        Long usuarioId = criarUsuario("membro_historico").getId();
        long membershipId = criarMembership(organizacaoId, usuarioId);

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENCERRADO\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(membershipId)));
    }

    @Test
    void rejeitaOrganizacaoNaoAtivaUsuarioInativoEReferenciasInexistentes() throws Exception {
        Long organizacaoId = criarOrganizacao();
        Usuario usuario = criarUsuario("membro_inativo");
        usuarioService.excluir(usuario.getId());

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuario.getId() + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/organizations/999999/memberships")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaOrganizacaoSuspensaOuEncerrada() throws Exception {
        Usuario usuario = criarUsuario("membro_org_inativa");
        Long suspensaId = criarOrganizacao();
        var suspensa = organizacaoRepository.findById(suspensaId).orElseThrow();
        suspensa.suspender();
        organizacaoRepository.saveAndFlush(suspensa);

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", suspensaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuario.getId() + "}"))
                .andExpect(status().isConflict());

        Long encerradaId = organizacaoService.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Kaneko Encerrada Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        var encerrada = organizacaoRepository.findById(encerradaId).orElseThrow();
        encerrada.encerrar();
        organizacaoRepository.saveAndFlush(encerrada);

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", encerradaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuario.getId() + "}"))
                .andExpect(status().isConflict());
    }

    private long criarMembership(Long organizacaoId, Long usuarioId) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private Long criarOrganizacao() {
        return organizacaoService.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Membership Ltda", "Kaneko Membership",
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
    }

    private Usuario criarUsuario(String login) {
        return usuarioService.criar(new UsuarioRequest(
                "Usuario Membership", login, "senha123", null, null, PerfilUsuario.OPERADOR));
    }
}
