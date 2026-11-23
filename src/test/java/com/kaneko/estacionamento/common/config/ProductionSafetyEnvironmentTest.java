package com.kaneko.estacionamento.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyEnvironmentTest {
    private final ProductionSafetyEnvironment guard = new ProductionSafetyEnvironment();

    @Test
    void desenvolvimentoETesteMantemContratoAtual() {
        var env = new MockEnvironment().withProperty("app.security.enabled", "false");
        env.setActiveProfiles("test");
        assertThatCode(() -> guard.postProcessEnvironment(env, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    void configuracaoExplicitaSeguraAceitaProxyLoopback() {
        assertThatCode(() -> check(Map.of())).doesNotThrowAnyException();
    }

    @Test
    void segurancaDemoBootstrapESchemaInsegurosImpedemInicializacao() {
        for (var entry : Map.of(
                "app.security.enabled", "false",
                "app.demo-data.enabled", "true",
                "app.bootstrap-admin.enabled", "true",
                "spring.jpa.hibernate.ddl-auto", "update",
                "spring.jpa.show-sql", "true",
                "spring.flyway.enabled", "true",
                "spring.flyway.clean-disabled", "false",
                "spring.flyway.baseline-on-migrate", "true").entrySet()) {
            assertThatThrownBy(() -> check(Map.of(entry.getKey(), entry.getValue())))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(entry.getKey());
        }
    }

    @Test
    void chavePadraoOuCurtaNuncaEhExpostaNoErro() {
        for (String value : new String[] {"curta", "kaneko-troque-esta-chave-secreta-em-producao-32+bytes"}) {
            assertThatThrownBy(() -> check(Map.of("app.jwt.secret", value)))
                    .hasMessageContaining("app.jwt.secret").hasMessageNotContaining(value);
        }
    }

    @Test
    void bancoSemIdentidadePropriaEProibido() {
        for (var entry : Map.of("spring.datasource.username", "postgres",
                "spring.datasource.password", "root", "spring.datasource.url", "jdbc:h2:mem:test").entrySet()) {
            assertThatThrownBy(() -> check(Map.of(entry.getKey(), entry.getValue())))
                    .hasMessageContaining(entry.getKey()).hasMessageNotContaining(entry.getValue());
        }
    }

    @Test
    void exigeOrigemHttpsETransporteConfigurado() {
        for (var entry : Map.of("app.production.public-url", "http://parking.example.com",
                "server.address", "0.0.0.0", "server.forward-headers-strategy", "framework",
                "app.production.tls-mode", "qualquer").entrySet()) {
            assertThatThrownBy(() -> check(Map.of(entry.getKey(), entry.getValue())))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> check(Map.of("app.production.public-url", "https://user:password@parking.example.com")))
                .hasMessageNotContaining("password");
    }

    @Test
    void sslDiretoExigeSslRealECorsNaoPodeEstarAmplo() {
        assertThatThrownBy(() -> check(Map.of("app.production.tls-mode", "DIRECT")))
                .hasMessageContaining("server.ssl.enabled");
        assertThatCode(() -> check(Map.of("app.production.tls-mode", "DIRECT", "server.ssl.enabled", "true")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> check(Map.of("app.cors.allowed-origins", "*")))
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    void naoPermiteMisturarProdComPerfisDeTesteOuDemo() {
        var env = environment(Map.of());
        env.setActiveProfiles("prod", "test");
        assertThatThrownBy(() -> guard.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("profiles");
    }

    @Test
    void perfilProdDefaultTambemEhProtegidoERegistroPrecedeBeans() {
        var env = new MockEnvironment().withProperty("app.security.enabled", "false");
        env.setDefaultProfiles("prod");
        assertThatThrownBy(() -> guard.postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("app.security.enabled");
        assertThatCode(() -> {
            var registry = new org.springframework.core.io.ClassPathResource("META-INF/spring.factories");
            try (var stream = registry.getInputStream()) {
                var properties = new java.util.Properties();
                properties.load(stream);
                org.assertj.core.api.Assertions.assertThat(properties.getProperty("org.springframework.boot.EnvironmentPostProcessor"))
                        .contains(ProductionSafetyEnvironment.class.getName());
            }
        }).doesNotThrowAnyException();
    }

    private void check(Map<String, String> overrides) {
        guard.postProcessEnvironment(environment(overrides), new SpringApplication());
    }

    private MockEnvironment environment(Map<String, String> overrides) {
        Map<String, Object> values = new HashMap<>(Map.ofEntries(
                Map.entry("app.security.enabled", "true"),
                Map.entry("app.bootstrap-admin.enabled", "false"),
                Map.entry("app.demo-data.enabled", "false"),
                Map.entry("app.jwt.secret", "unit-test-only-random-key-936c0a42-92bd-4413-b44d"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                Map.entry("spring.jpa.show-sql", "false"),
                Map.entry("spring.flyway.enabled", "false"),
                Map.entry("spring.flyway.clean-disabled", "true"),
                Map.entry("spring.flyway.baseline-on-migrate", "false"),
                Map.entry("spring.datasource.url", "jdbc:postgresql://db.example.invalid/parking"),
                Map.entry("spring.datasource.username", "parking_app"),
                Map.entry("spring.datasource.password", "unit-test-only-database-pass-93a7"),
                Map.entry("app.production.public-url", "https://parking.example.com"),
                Map.entry("app.production.tls-mode", "LOOPBACK_PROXY"),
                Map.entry("server.address", "127.0.0.1"),
                Map.entry("server.forward-headers-strategy", "none")));
        values.putAll(overrides);
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.getPropertySources().addFirst(new MapPropertySource("fixture", values));
        return env;
    }
}
