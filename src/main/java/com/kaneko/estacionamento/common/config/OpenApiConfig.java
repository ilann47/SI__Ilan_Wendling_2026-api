package com.kaneko.estacionamento.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentacao OpenAPI/Swagger e o esquema de seguranca (Bearer
 * JWT), habilitando o botao "Authorize" na UI do Swagger.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";

    @Bean
    public OpenAPI estacionamentoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Estacionamento Kaneko API")
                        .version("v1")
                        .description("API de gestao de estacionamento: organizacoes, Membership, RBAC contextual e selecao "
                                + "de contexto multiempresa em /api/v1, "
                                + "e APIs legadas de patio (mensalista e avulso), cadastros, conveniencia, "
                                + "notas fiscais e financeiro."))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA))
                .components(new Components().addSecuritySchemes(ESQUEMA,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Informe o token obtido em POST /api/auth/login.")));
    }
}
