package com.kaneko.estacionamento.vendas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class PedidoVendaRemovidoTenantApiTest {
    private static final String ANTIGA = "/api/v1/administrative-sales";

    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenants;

    @Test
    void nemAdministradorPodeUsarAsRotasAposentadas() throws Exception {
        var fixture = tenants.criarTenantAdmin();
        for (var request : List.of(
                get(ANTIGA), get(ANTIGA + "/1"), get(ANTIGA + "/1/documents"),
                post(ANTIGA), put(ANTIGA + "/1"), post(ANTIGA + "/1/confirmation"),
                post(ANTIGA + "/1/cancellation"), post(ANTIGA + "/1/outbound-note"))) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }
}
