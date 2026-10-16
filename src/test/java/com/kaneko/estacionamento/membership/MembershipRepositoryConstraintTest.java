package com.kaneko.estacionamento.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MembershipRepositoryConstraintTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizacaoService organizacaoService;
    @Autowired
    private UsuarioService usuarioService;

    @Test
    void bancoImpedeDoisVinculosCorrentes() {
        Long organizacaoId = criarOrganizacao();
        Long usuarioId = criarUsuario().getId();

        inserir(organizacaoId, usuarioId, "ATIVO", null);
        assertThatThrownBy(() -> inserir(organizacaoId, usuarioId, "ATIVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoPreservaHistoricoEncerradoEPermiteNovoVinculo() {
        Long organizacaoId = criarOrganizacao();
        Long usuarioId = criarUsuario().getId();

        long primeiro = inserir(organizacaoId, usuarioId, "ATIVO", null);
        jdbc.update("UPDATE membro_organizacao SET status='ENCERRADO', encerrado_em=now() WHERE id=?", primeiro);
        long segundo = inserir(organizacaoId, usuarioId, "ATIVO", null);
        assertThat(segundo).isNotEqualTo(primeiro);
    }

    @Test
    void bancoRecusaEstadoComDatasIncoerentes() {
        Long organizacaoId = criarOrganizacao();
        Long usuarioId = criarUsuario().getId();

        assertThatThrownBy(() -> inserir(organizacaoId, usuarioId, "SUSPENSO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long inserir(Long organizacaoId, Long usuarioId, String status, String suspensoEm) {
        return jdbc.queryForObject("""
                INSERT INTO membro_organizacao
                    (organizacao_id, usuario_id, status, origem, entrou_em, suspenso_em,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, 'ADMINISTRATIVA', now(), CAST(? AS timestamptz), 0, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, usuarioId, status, suspensoEm);
    }

    private Long criarOrganizacao() {
        return organizacaoService.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Kaneko Constraint Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
    }

    private Usuario criarUsuario() {
        return usuarioService.criar(new UsuarioRequest(
                "Usuario Constraint", "membro_constraint", "senha123",
                null, null, PerfilUsuario.OPERADOR));
    }
}
