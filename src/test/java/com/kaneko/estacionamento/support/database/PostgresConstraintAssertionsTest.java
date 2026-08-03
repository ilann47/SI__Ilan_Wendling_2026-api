package com.kaneko.estacionamento.support.database;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostgresConstraintAssertionsTest {

    @Test
    void validaSqlStateENomeDaConstraint() {
        assertThatCode(() -> PostgresConstraintAssertions.assertViolation(
                () -> lancar("23505", "uk_organizacao_documento"),
                "23505",
                "uk_organizacao_documento"))
                .doesNotThrowAnyException();
    }

    @Test
    void falhaQuandoAConstraintNaoForAEsperada() {
        assertThatThrownBy(() -> PostgresConstraintAssertions.assertViolation(
                () -> lancar("23505", "uk_organizacao_documento"),
                "23505",
                "fk_outro_tenant"))
                .isInstanceOf(AssertionError.class);
    }

    private static void lancar(String sqlState, String constraint) {
        SQLException sql = new SQLException(
                "violates constraint \"" + constraint + "\"", sqlState);
        throw new DataIntegrityViolationException("integrity violation", sql);
    }
}
