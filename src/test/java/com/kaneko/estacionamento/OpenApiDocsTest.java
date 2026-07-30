package com.kaneko.estacionamento;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que a documentacao OpenAPI (springdoc) sobe no contexto Boot 4 e
 * publica os endpoints do cadastro de Pais.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void publicaOpenApiComEndpointsDePais() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/paises']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{id}'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{id}'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/memberships'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/memberships'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/memberships/{membershipId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/memberships/{membershipId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/organizations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/active-organization'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/permissions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments/{assignmentId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/parking-facilities'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/parking-facilities'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/venues'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/venues'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/events'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventoId}/parking-allocations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/parking-allocations/{alocacaoId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/parking-allocations/{alocacaoId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventoId}/parking-products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventoId}/parking-products'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/parking-products/{produtoId}/price-tiers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/parking-products/{produtoId}/price-tiers'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/parking-products/{produtoId}/publication'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventoId}/publication'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventoId}/availability'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventoId}/sales-opening'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inventory-holds'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inventory-holds/{holdId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inventory-holds/{holdId}/release'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}/manual-confirmation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}/cancellation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}/credentials'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credentials/{credentialId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credentials/{credentialId}/qr-code'].post").exists());
    }
}
