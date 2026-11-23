package com.kaneko.estacionamento.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class AuditoriaEnterpriseTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired JwtService jwt;
    @Autowired RegistroAuditoriaRepository auditorias;
    @Autowired JdbcTemplate jdbc;

    private Usuario admin;
    private Usuario operador;
    private String tokenAdmin;

    @BeforeEach
    void setup() {
        admin = usuario("audit_admin", PerfilUsuario.ADMIN);
        operador = usuario("audit_operador", PerfilUsuario.OPERADOR);
        tokenAdmin = bearer(jwt.gerarToken(admin.getLogin(), "OPERADOR"));
    }

    @Test
    void registraCriacaoComAtorTenantTraceOrigemESnapshotRedigido() throws Exception {
        long organizacaoId = criarOrganizacao("trace-auditoria-criacao");

        RegistroAuditoria registro = ultimo("ORGANIZACAO_CRIADA", "ORGANIZACAO", organizacaoId);
        assertThat(registro.getOrganizacaoId()).isEqualTo(organizacaoId);
        assertThat(registro.getAtorTipo()).isEqualTo(TipoAtorAuditoria.HUMANO);
        assertThat(registro.getAtorId()).isEqualTo(admin.getId());
        assertThat(registro.getOrigem()).isEqualTo(OrigemAuditoria.API);
        assertThat(registro.getTraceId()).isEqualTo("trace-auditoria-criacao");
        assertThat(registro.getIpOrigem()).isNotBlank();
        assertThat(registro.getClasseRetencao()).isEqualTo(ClasseRetencaoAuditoria.CONFIGURACAO);
        assertThat(registro.getEstadoAnterior()).isNull();
        assertThat(registro.getEstadoPosterior()).containsEntry("status", "ATIVA");
        assertThat(registro.getEstadoPosterior()).doesNotContainKey("documento");
        assertThat(registro.getVersaoPosterior()).isZero();
    }

    @Test
    void registraAntesDepoisEVersoesSomenteQuandoMutacaoTemSucesso() throws Exception {
        long organizacaoId = criarOrganizacao("trace-auditoria-update");
        long membershipId = criarMembership(organizacaoId, admin.getId(), tokenAdmin);
        String contextual = selecionarOrganizacao(organizacaoId, tokenAdmin);

        mvc.perform(patch("/api/v1/organizations/{id}", organizacaoId)
                        .header("Authorization", contextual).header("If-Match", "\"0\"")
                        .header("X-Trace-Id", "trace-update-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Kaneko Auditada Ltda\"}"))
                .andExpect(status().isOk());

        RegistroAuditoria alteracao = ultimo("ORGANIZACAO_ATUALIZADA", "ORGANIZACAO", organizacaoId);
        assertThat(alteracao.getEstadoAnterior()).containsEntry("razaoSocial", "Kaneko Auditoria Ltda");
        assertThat(alteracao.getEstadoPosterior()).containsEntry("razaoSocial", "Kaneko Auditada Ltda");
        assertThat(alteracao.getVersaoAnterior()).isZero();
        assertThat(alteracao.getVersaoPosterior()).isEqualTo(1L);
        assertThat(alteracao.getTraceId()).isEqualTo("trace-update-ok");

        long quantidade = auditorias.count();
        mvc.perform(patch("/api/v1/organizations/{id}", organizacaoId)
                        .header("Authorization", contextual).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Nao Deve Persistir Ltda\"}"))
                .andExpect(status().isPreconditionFailed());
        assertThat(auditorias.count()).isEqualTo(quantidade);
        assertThat(membershipId).isPositive();
    }

    @Test
    void auditaMembershipConcessaoERevogacaoComMotivo() throws Exception {
        long organizacaoId = criarOrganizacao("trace-auditoria-rbac");
        criarMembership(organizacaoId, admin.getId(), tokenAdmin);
        String contextual = selecionarOrganizacao(organizacaoId, tokenAdmin);
        long membershipOperador = criarMembership(organizacaoId, operador.getId(), contextual);

        RegistroAuditoria membership = ultimo("MEMBERSHIP_CRIADA", "MEMBERSHIP", membershipOperador);
        assertThat(membership.getEstadoPosterior()).containsEntry("status", "ATIVO");
        assertThat(membership.getClasseRetencao()).isEqualTo(ClasseRetencaoAuditoria.SEGURANCA);

        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipOperador)
                        .header("Authorization", contextual).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"ADMIN_ORGANIZACAO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long atribuicaoId = ((Number) JsonPath.read(body, "$.id")).longValue();
        RegistroAuditoria concessao = ultimo("PAPEL_ATRIBUIDO", "ATRIBUICAO_ACESSO", atribuicaoId);
        assertThat(concessao.getEstadoPosterior()).containsEntry("papel", "ADMIN_ORGANIZACAO");

        mvc.perform(delete("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments/{assignmentId}",
                        organizacaoId, membershipOperador, atribuicaoId)
                        .header("Authorization", contextual).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fim da responsabilidade administrativa\"}"))
                .andExpect(status().isNoContent());
        RegistroAuditoria revogacao = ultimo("ACESSO_REVOGADO", "ATRIBUICAO_ACESSO", atribuicaoId);
        assertThat(revogacao.getMotivo()).isEqualTo("Fim da responsabilidade administrativa");
        assertThat(revogacao.getVersaoAnterior()).isZero();
        assertThat(revogacao.getVersaoPosterior()).isEqualTo(1L);
    }

    @Test
    void bancoImpedeAlteracaoDaTrilha() throws Exception {
        long organizacaoId = criarOrganizacao("trace-auditoria-imutavel");
        Long id = ultimo("ORGANIZACAO_CRIADA", "ORGANIZACAO", organizacaoId).getId();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE registro_auditoria SET acao = 'ADULTERADA' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    private long criarOrganizacao(String traceId) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations")
                        .header("Authorization", tokenAdmin).header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document":"12.345.678/0001-95","legalName":"Kaneko Auditoria Ltda",
                                 "currency":"BRL","timeZone":"America/Sao_Paulo","region":"BR-PR",
                                 "plan":"ENTERPRISE"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarMembership(long organizacaoId, long usuarioId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String selecionarOrganizacao(long organizacaoId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    private RegistroAuditoria ultimo(String acao, String recursoTipo, long recursoId) {
        return auditorias.findAll().stream()
                .filter(r -> acao.equals(r.getAcao()) && recursoTipo.equals(r.getRecursoTipo())
                        && recursoId == r.getRecursoId())
                .max(Comparator.comparing(RegistroAuditoria::getOcorridoEm))
                .orElseThrow();
    }

    private Usuario usuario(String login, PerfilUsuario perfil) {
        return usuarios.criar(new UsuarioRequest("Usuario Auditoria", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
