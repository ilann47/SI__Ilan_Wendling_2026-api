package com.kaneko.estacionamento.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoAllScreensSeedService {

    private static final String SEED_RESOURCE = "db/demo/all-screens.sql";

    private final JdbcTemplate jdbc;

    public DemoAllScreensSeedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void seed(String adminLogin, String operatorLogin) {
        String admin = normalizeLogin(adminLogin, "administrador");
        String operator = normalizeLogin(operatorLogin, "operador");
        jdbc.queryForObject(
                "select set_config('app.demo_admin_login', ?, true)", String.class, admin);
        jdbc.queryForObject(
                "select set_config('app.demo_operator_login', ?, true)", String.class, operator);
        jdbc.execute(loadSeed());
    }

    private static String loadSeed() {
        try {
            return new ClassPathResource(SEED_RESOURCE)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Nao foi possivel carregar o seed demo.", exception);
        }
    }

    private static String normalizeLogin(String login, String profile) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("O login do " + profile + " demo e obrigatorio.");
        }
        return login.trim();
    }
}
