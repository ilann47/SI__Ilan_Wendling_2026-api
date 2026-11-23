package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatioRascunhoNfseRepository {
    private static final MilionesimosConverter ESCALA = new MilionesimosConverter();
    private static final String ORIGEM = """
            FROM patio_evento_fiscal f
            JOIN patio_evento_estadia s ON s.organizacao_id=f.organizacao_id AND s.movimentacao_id=f.movimentacao_id
            JOIN movimentacao m ON m.organizacao_id=f.organizacao_id AND m.id=f.movimentacao_id
            JOIN veiculo v ON v.organizacao_id=f.organizacao_id AND v.id=m.veiculo_id
            JOIN evento e ON e.organizacao_id=f.organizacao_id AND e.id=s.evento_id
            JOIN patio p ON p.organizacao_id=f.organizacao_id AND p.id=s.patio_id
            LEFT JOIN ordem_servico os ON os.organizacao_id=f.organizacao_id
              AND os.movimentacao_patio_id=f.movimentacao_id AND os.id=f.ordem_servico_id
            LEFT JOIN item_ordem_servico i ON i.organizacao_id=os.organizacao_id
              AND i.ordem_servico_id=os.id AND i.sequencia=1 AND i.servico_id IS NOT NULL
            WHERE f.organizacao_id=?
            """;
    private static final String RESUMO = """
            SELECT f.movimentacao_id, f.ordem_servico_id, os.numero ordem_servico_numero,
                   e.nome evento_nome, p.nome patio_nome, v.placa, f.valor_micros,
                   f.status, f.criado_em, f.versao, f.tomador_documento IS NOT NULL tomador_informado,
                   i.servico_id, i.descricao_snapshot servico_nome
            """;
    private static final String LISTAGEM = ORIGEM + """
            AND f.status='PENDENTE_EMISSAO'
            AND (?='' OR strpos(lower(v.placa),lower(?))>0
                      OR strpos(lower(os.numero),lower(?))>0
                      OR strpos(lower(e.nome),lower(?))>0)
            """;
    private final JdbcTemplate jdbc;

    PatioRascunhoNfseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Page<PatioRascunhoNfseService.Rascunho> listar(long org, String busca, Pageable page) {
        long total = jdbc.queryForObject("SELECT count(*) " + LISTAGEM, Long.class, org, busca, busca, busca, busca);
        if (total == 0) return Page.empty(page);
        List<PatioRascunhoNfseService.Rascunho> resultados = jdbc.query(RESUMO + LISTAGEM + """
                ORDER BY f.criado_em DESC, f.movimentacao_id DESC LIMIT ? OFFSET ?
                """, (rs, n) -> mapear(rs, false), org, busca, busca, busca, busca, page.getPageSize(), page.getOffset());
        return new PageImpl<>(resultados, page, total);
    }

    Optional<PatioRascunhoNfseService.Rascunho> buscar(long org, long mov, boolean bloquear) {
        return jdbc.query(RESUMO.stripTrailing() + ", f.tomador_documento, f.tomador_nome\n" + ORIGEM
                + " AND f.movimentacao_id=?" + (bloquear ? " FOR UPDATE OF f" : ""),
                (rs, n) -> mapear(rs, true), org, mov).stream().findFirst();
    }

    void atualizarTomador(long org, long mov, PatioTomador tomador) {
        jdbc.update("""
                UPDATE patio_evento_fiscal SET tomador_documento=?, tomador_nome=?, versao=versao+1
                WHERE organizacao_id=? AND movimentacao_id=? AND status='PENDENTE_EMISSAO'
                """, tomador == null ? null : tomador.documento(), tomador == null ? null : tomador.nome(), org, mov);
    }

    private static PatioRascunhoNfseService.Rascunho mapear(ResultSet rs, boolean detalhe) throws SQLException {
        return new PatioRascunhoNfseService.Rascunho(rs.getLong("movimentacao_id"),
                rs.getObject("ordem_servico_id", Long.class), rs.getString("ordem_servico_numero"),
                rs.getString("evento_nome"), rs.getString("patio_nome"), rs.getString("placa"),
                ESCALA.convertToEntityAttribute(rs.getBigDecimal("valor_micros")).toPlainString(),
                rs.getString("status"), rs.getObject("criado_em", OffsetDateTime.class), rs.getLong("versao"),
                rs.getBoolean("tomador_informado"), detalhe
                        ? PatioTomador.salvo(rs.getString("tomador_documento"), rs.getString("tomador_nome")) : null,
                rs.getObject("servico_id", Long.class), rs.getString("servico_nome"));
    }
}
