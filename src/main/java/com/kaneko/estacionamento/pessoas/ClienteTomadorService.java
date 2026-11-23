package com.kaneko.estacionamento.pessoas;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteTomadorService {
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ClienteTomadorService(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** Recebe a identificação canônica já validada pelo fluxo fiscal. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cadastrarSeNovo(long organizacaoId, String documento, String nome) {
        String tipo = documento.length() == 11 ? TipoPessoa.FISICA.name() : TipoPessoa.JURIDICA.name();
        // A comparação normalizada também protege cadastros históricos ainda mascarados.
        // O índice parcial resolve concorrência entre inserções com o documento canônico.
        var inseridos = jdbc.query("""
                INSERT INTO cliente (organizacao_id, nome, tipo, documento, limite_credito_micros,
                                     ativo, origem_cadastro_fiscal)
                SELECT ?, ?, ?, ?, 0, true, true
                WHERE NOT EXISTS (
                    SELECT 1 FROM cliente
                    WHERE organizacao_id = ? AND upper(translate(documento, './-', '')) = ?
                )
                ON CONFLICT (organizacao_id, documento) WHERE organizacao_id IS NOT NULL DO NOTHING
                RETURNING id
                """, (rs, row) -> rs.getLong("id"), organizacaoId, nome, tipo, documento, organizacaoId, documento);
        inseridos.forEach(id -> auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "CLIENTE_CRIADO_PELO_TOMADOR_FISCAL", "CLIENTE", id, null, null, null, null,
                Map.of("origem", "RASCUNHO_NFSE_PATIO"), null, null, ClasseRetencaoAuditoria.PRIVACIDADE)));
    }
}
