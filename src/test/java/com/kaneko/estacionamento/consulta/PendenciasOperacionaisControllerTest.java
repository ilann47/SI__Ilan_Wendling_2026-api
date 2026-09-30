package com.kaneko.estacionamento.consulta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.kaneko.estacionamento.support.tenant.*;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class PendenciasOperacionaisControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTestSupport tenant;

    @Test void contaTodoConjuntoMasLimitaItensERespeitaTenant() throws Exception {
        var a = tenant.criarTenantAdmin(); var b = tenant.criarTenantAdmin();
        criarServico(a.organizacaoId(), "OS-1", "CONCLUIDA");
        criarServico(a.organizacaoId(), "OS-2", "CONCLUIDA");
        criarServico(a.organizacaoId(), "OS-Rascunho", "RASCUNHO");
        criarServico(b.organizacaoId(), "OS-Outra", "CONCLUIDA");
        mvc.perform(get("/api/v1/operational-pendencies").param("limit", "1")
                        .header("Authorization", a.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'SERVICO_SEM_NOTA')].total").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'SERVICO_SEM_NOTA')].itens[0].titulo").value(org.hamcrest.Matchers.contains("Ordem OS-1 concluída sem nota")));
    }

    @Test void semPermissaoNaoRecebeDadosETokenGlobalNaoAcessa() throws Exception {
        var a = tenant.criarTenantAdmin();
        criarServico(a.organizacaoId(), "OS-restrita", "CONCLUIDA");
        var u = tenant.criarUsuario("pend_" + UUID.randomUUID(), PerfilUsuario.OPERADOR);
        tenant.criarMembership(a.organizacaoId(), u.getId(), a.tokenContextual());
        String contextual = tenant.selecionarOrganizacao(a.organizacaoId(), tenant.autenticar(u.getLogin(), "senha123"));
        mvc.perform(get("/api/v1/operational-pendencies").header("Authorization", contextual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.grupos.length()").value(0));
        mvc.perform(get("/api/v1/operational-pendencies").header("Authorization", a.tokenGlobal()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operational-pendencies").param("limit", "0")
                        .header("Authorization", a.tokenContextual())).andExpect(status().isBadRequest());
    }

    @Test void naoConfundeDocumentoJaGeradoComPendenciaEIncluiVencidasAntigas() throws Exception {
        var a = tenant.criarTenantAdmin();
        long os = criarServico(a.organizacaoId(), "OS-com-nota", "CONCLUIDA");
        Long cliente = jdbc.queryForObject("SELECT cliente_id FROM ordem_servico WHERE id=?", Long.class, os);
        jdbc.update("INSERT INTO nota_servico(organizacao_id,cliente_id,numero,ordem_servico_id) VALUES (?,?,'NS-pendencias',?)", a.organizacaoId(), cliente, os);
        jdbc.update("""
            INSERT INTO conta_receber(organizacao_id,cliente_id,valor_original,valor_total,data_emissao,data_vencimento,situacao)
            VALUES (?,?,100,100,current_date-90,current_date-30,'PENDENTE'),
                   (?,?,100,100,current_date,current_date+1,'PENDENTE'),
                   (?,?,100,100,current_date-90,current_date-30,'CANCELADA')
            """, a.organizacaoId(), cliente, a.organizacaoId(), cliente, a.organizacaoId(), cliente);
        mvc.perform(get("/api/v1/operational-pendencies").header("Authorization", a.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'SERVICO_SEM_NOTA')].total").value(org.hamcrest.Matchers.contains(0)))
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'CONTA_RECEBER_VENCIDA')].itens[0].valor").value(org.hamcrest.Matchers.contains(100.0)));
    }

    @Test void preservaDespesaAvulsaVencidaSemFornecedor() throws Exception {
        var a = tenant.criarTenantAdmin();
        jdbc.update("""
            INSERT INTO contas_pagar_avulsa(organizacao_id,descricao,valor_original,valor_total,data_emissao,data_vencimento)
            VALUES (?,'Aluguel',50,50,current_date-40,current_date-10)
            """, a.organizacaoId());
        mvc.perform(get("/api/v1/operational-pendencies").header("Authorization", a.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'DESPESA_AVULSA_VENCIDA')].total").value(org.hamcrest.Matchers.contains(1)));
    }

    private long criarServico(Long org, String numero, String estado) {
        Long cliente = jdbc.queryForObject("""
            INSERT INTO cliente (organizacao_id,nome,tipo,documento,limite_credito,ativo,created_at,updated_at)
            VALUES (?,'Cliente de serviços','FISICA',?,0,true,now(),now()) RETURNING id
            """, Long.class, org, UUID.randomUUID().toString().substring(0, 14));
        return jdbc.queryForObject("""
            INSERT INTO ordem_servico(organizacao_id,cliente_id,numero,status,data_abertura,iniciada_em,concluida_em)
            VALUES (?,?,?,?,current_date,CASE WHEN ?='CONCLUIDA' THEN now() END,CASE WHEN ?='CONCLUIDA' THEN now() END) RETURNING id
            """, Long.class, org, cliente, numero, estado, estado, estado);
    }
}
