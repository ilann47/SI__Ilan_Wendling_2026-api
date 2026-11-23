package com.kaneko.estacionamento.opcoes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class OperationalOptionsApiTest {
    private static final String PATH = "/api/v1/operational-options/{kind}";

    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired JdbcTemplate jdbc;

    @Test
    void buscaVinculoPorNomeENaoRevelaVinculoDeOutraOrganizacao() throws Exception {
        TenantFixture a = tenant.criarTenantAdmin();
        TenantFixture b = tenant.criarTenantAdmin();
        jdbc.update("UPDATE usuario SET nome = ? WHERE id = ?", "Usuário de teste", a.usuarioId());
        mvc.perform(get(PATH, "memberships").header("Authorization", a.tokenContextual())
                        .param("q", "USUARIO DE TESTE").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a.membershipId()))
                .andExpect(jsonPath("$.content[0].label").value("Usuário de teste"))
                .andExpect(jsonPath("$.content[0].version").value(0))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].login").doesNotExist());
        mvc.perform(get(PATH, "memberships").header("Authorization", a.tokenContextual())
                        .param("id", b.membershipId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void exigeContextoEPermissaoDeAcaoParaListar() throws Exception {
        TenantFixture admin = tenant.criarTenantAdmin();
        var operador = tenant.criarUsuario("opcoes_sem_papel_" + admin.usuarioId(), PerfilUsuario.OPERADOR);
        tenant.criarMembership(admin.organizacaoId(), operador.getId(), admin.tokenContextual());
        String token = tenant.selecionarOrganizacao(admin.organizacaoId(),
                tenant.autenticar(operador.getLogin(), "senha123"));
        mvc.perform(get(PATH, "memberships").header("Authorization", admin.tokenGlobal()))
                .andExpect(status().isForbidden());
        for (String kind : List.of("access-events", "audit-events", "access-facilities", "blocking-credentials", "sales-products",
                "sales-holds", "ordering-holds", "sales-orders", "issuing-orders", "sales-credentials", "memberships")) {
            mvc.perform(get(PATH, kind).header("Authorization", token)).andExpect(status().isForbidden());
        }
    }

    @Test
    void consultaCadaTipoEPaginaNoBancoComEscopo() throws Exception {
        TenantFixture a = tenant.criarTenantAdmin();
        for (String kind : List.of("access-events", "audit-events", "access-facilities", "blocking-credentials", "sales-products",
                "sales-holds", "ordering-holds", "sales-orders", "issuing-orders", "sales-credentials")) {
            mvc.perform(get(PATH, kind).header("Authorization", a.tokenContextual()).param("page", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get(PATH, "memberships").header("Authorization", a.tokenContextual()).param("size", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH, "desconhecido").header("Authorization", a.tokenContextual()))
                .andExpect(status().isNotFound());
    }
}
