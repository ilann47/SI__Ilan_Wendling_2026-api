package com.kaneko.estacionamento.common.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** Rejects unsafe production configuration before beans, datasource or seeds start. */
public final class ProductionSafetyEnvironment implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (!environment.acceptsProfiles(Profiles.of("prod", "production"))) return;
        var problems = new ArrayList<String>();
        if (profiles.stream().anyMatch(profile -> List.of("test", "dev", "demo", "local").contains(profile))) {
            problems.add("spring.profiles.active não pode misturar produção e perfis locais");
        }
        require(environment, problems, "app.security.enabled", "true");
        require(environment, problems, "app.bootstrap-admin.enabled", "false");
        require(environment, problems, "app.demo-data.enabled", "false");
        require(environment, problems, "spring.jpa.hibernate.ddl-auto", "validate");
        require(environment, problems, "spring.jpa.show-sql", "false");
        require(environment, problems, "spring.flyway.enabled", "false");
        require(environment, problems, "spring.flyway.clean-disabled", "true");
        require(environment, problems, "spring.flyway.baseline-on-migrate", "false");

        String jwt = value(environment, "app.jwt.secret");
        if (jwt.getBytes(StandardCharsets.UTF_8).length < 32
                || jwt.toLowerCase(Locale.ROOT).contains("troque-esta-chave")
                || jwt.toLowerCase(Locale.ROOT).contains("changeme")
                || jwt.chars().distinct().count() < 8) {
            problems.add("app.jwt.secret precisa de chave própria forte, sem valor padrão");
        }
        if (!value(environment, "spring.datasource.url").startsWith("jdbc:postgresql://")) {
            problems.add("spring.datasource.url precisa identificar PostgreSQL explicitamente");
        }
        String user = value(environment, "spring.datasource.username").toLowerCase(Locale.ROOT);
        if (user.isBlank() || List.of("postgres", "root", "sa").contains(user)) {
            problems.add("spring.datasource.username precisa de identidade própria sem superusuário");
        }
        String password = value(environment, "spring.datasource.password");
        if (password.length() < 12 || List.of("root", "postgres", "password", "admin123").contains(password)) {
            problems.add("spring.datasource.password precisa ser configurada sem padrão de desenvolvimento");
        }
        if (!httpsOrigin(value(environment, "app.production.public-url"))) {
            problems.add("app.production.public-url precisa de origem HTTPS explícita sem credenciais, caminho ou consulta");
        }
        // The application currently uses a same-origin reverse proxy. Merely setting a
        // fictional CORS allowlist must not make a cross-origin deployment look supported.
        if (!value(environment, "app.cors.allowed-origins").isBlank()) {
            problems.add("app.cors.allowed-origins não é suportado: publique frontend/API na mesma origem HTTPS");
        }
        String mode = value(environment, "app.production.tls-mode");
        if ("DIRECT".equals(mode)) {
            require(environment, problems, "server.ssl.enabled", "true");
            require(environment, problems, "server.forward-headers-strategy", "none");
        } else if ("LOOPBACK_PROXY".equals(mode)) {
            require(environment, problems, "server.address", "127.0.0.1");
            require(environment, problems, "server.forward-headers-strategy", "none");
        } else {
            problems.add("app.production.tls-mode precisa ser DIRECT ou LOOPBACK_PROXY");
        }
        if (!problems.isEmpty()) {
            // Property names only: never include configuration values in startup errors.
            throw new IllegalStateException("Configuração de produção recusada: " + String.join("; ", problems));
        }
    }

    private static void require(ConfigurableEnvironment environment, List<String> problems,
                                String name, String expected) {
        if (!expected.equalsIgnoreCase(value(environment, name))) problems.add(name + " precisa ser " + expected);
    }

    private static String value(ConfigurableEnvironment environment, String name) {
        try {
            String result = environment.getProperty(name, "");
            return result.contains("${") ? "" : result.trim();
        } catch (IllegalArgumentException unresolved) {
            return "";
        }
    }

    private static boolean httpsOrigin(String candidate) {
        try {
            URI uri = URI.create(candidate);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !uri.getHost().isBlank() && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
