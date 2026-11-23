package com.kaneko.estacionamento.financeiro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class FinanceiroRotasAtivasTest {
    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;

    @Test
    void rotaRemovidaNaoExisteEContasAPagarContinuamDisponiveis() throws Exception {
        var admin = tenant.criarTenantAdmin();
        mvc.perform(get("/api/contas-pagar-avulsas").header("Authorization", admin.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/contas-pagar").header("Authorization", admin.tokenContextual()))
                .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/contas-pagar-avulsas']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/contas-pagar']").exists());
    }
}
