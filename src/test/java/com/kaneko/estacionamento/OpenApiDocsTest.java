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
                .andExpect(jsonPath("$.paths['/api/v1/me/active-organization'].post").exists());
    }
}
