package com.kaneko.estacionamento.organizacao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrganizacaoRepositoryConstraintTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bancoImpedeDocumentoNormalizadoDuplicado() {
        inserir("12345678000195", "Primeira");

        assertThatThrownBy(() -> inserir("12345678000195", "Segunda"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaDocumentoNaoNormalizado() {
        assertThatThrownBy(() -> inserir("12.345.678/0001-95", "Mascarada"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(String documento, String razaoSocial) {
        jdbc.update("""
                INSERT INTO organizacao
                    (documento, razao_social, moeda, fuso_horario, regiao, plano, status, version, created_at, updated_at)
                VALUES (?, ?, 'BRL', 'America/Sao_Paulo', 'BR-PR', 'ENTERPRISE', 'ATIVA', 0, now(), now())
                """, documento, razaoSocial);
    }
}
