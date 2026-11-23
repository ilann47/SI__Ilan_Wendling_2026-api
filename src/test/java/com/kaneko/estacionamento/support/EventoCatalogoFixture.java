package com.kaneko.estacionamento.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class EventoCatalogoFixture {
    private EventoCatalogoFixture() {}

    public static long servicoEvento(JdbcTemplate jdbc, long org) {
        return servicoEvento(jdbc, org, "Estacionamento QA " + UUID.randomUUID(), true);
    }

    public static long servicoEvento(JdbcTemplate jdbc, long org, String nome, boolean ativo) {
        return jdbc.queryForObject("""
                INSERT INTO servico (organizacao_id,nome,ativo,created_at,updated_at)
                VALUES (?,?,?,now(),now()) RETURNING id
                """, Long.class, org, nome, ativo);
    }
}
