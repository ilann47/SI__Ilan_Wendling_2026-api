package com.kaneko.estacionamento.support.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.Locale;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.dao.DataIntegrityViolationException;

public final class PostgresConstraintAssertions {

    private PostgresConstraintAssertions() {
    }

    public static void assertViolation(
            ThrowingCallable operation,
            String expectedSqlState,
            String expectedConstraint) {
        Throwable thrown = catchThrowable(operation);

        assertThat(thrown)
                .as("a operacao deve violar uma constraint PostgreSQL")
                .isInstanceOf(DataIntegrityViolationException.class);

        SQLException sqlException = findSqlException(thrown);
        assertThat((Object) sqlException)
                .as("a causa deve preservar a SQLException do PostgreSQL")
                .isNotNull();
        assertThat(sqlException.getSQLState())
                .as("SQLSTATE da violacao")
                .isEqualTo(expectedSqlState);
        assertThat(messages(thrown))
                .as("nome da constraint violada")
                .contains(expectedConstraint.toLowerCase(Locale.ROOT));
    }

    private static SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String messages(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                result.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }
}
