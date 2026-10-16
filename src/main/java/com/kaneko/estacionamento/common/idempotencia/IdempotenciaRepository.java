package com.kaneko.estacionamento.common.idempotencia;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotenciaRepository {
    private final JdbcTemplate jdbc;

    public IdempotenciaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void bloquear(Long organizacaoId, Long atorId, String operacao, String chave) {
        String escopo = organizacaoId + ":" + atorId + ":" + operacao + ":" + chave;
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                ps -> ps.setString(1, escopo),
                rs -> null);
    }

    public Optional<RegistroIdempotencia> buscar(
            Long organizacaoId, Long atorId, String operacao, String chave) {
        return jdbc.query("""
                        SELECT request_hash, recurso_tipo, recurso_id
                        FROM requisicao_idempotente
                        WHERE organizacao_id = ? AND ator_id = ? AND operacao = ? AND chave = ?
                        """,
                (rs, rowNum) -> new RegistroIdempotencia(
                        rs.getString("request_hash"), rs.getString("recurso_tipo"),
                        rs.getLong("recurso_id")),
                organizacaoId, atorId, operacao, chave).stream().findFirst();
    }

    public void registrar(
            Long organizacaoId, Long atorId, String operacao, String chave,
            String requestHash, String recursoTipo, Long recursoId) {
        jdbc.update("""
                        INSERT INTO requisicao_idempotente (
                            organizacao_id, ator_id, operacao, chave,
                            request_hash, recurso_tipo, recurso_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                organizacaoId, atorId, operacao, chave,
                requestHash, recursoTipo, recursoId);
    }
}
