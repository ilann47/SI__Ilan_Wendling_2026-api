package com.kaneko.estacionamento.usuario;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UsuarioRepositoryConstraintTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bancoAceitaPerfilNeutroERecusaPerfilDesconhecido() {
        inserir("conta.neutra", "conta.neutra@example.com", "USUARIO");

        assertThatThrownBy(() -> inserir("conta.invalida", "conta.invalida@example.com", "SUPER_ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoGaranteUnicidadeCaseInsensitiveDeLoginEEmail() {
        inserir("Pessoa.Unica", "Pessoa.Unica@example.com", "USUARIO");

        assertThatThrownBy(() -> inserir("pessoa.unica", "outro@example.com", "USUARIO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(String login, String email, String perfil) {
        jdbc.update("""
                INSERT INTO usuario (nome, login, senha, email, perfil, ativo, created_at, updated_at)
                VALUES ('Conta', ?, 'hash-nao-utilizado', ?, ?, true, now(), now())
                """, login, email, perfil);
    }
}
