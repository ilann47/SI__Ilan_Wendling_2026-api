package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotaServicoListagemService {
    public enum Origem { ESTACIONAMENTO, MANUAL, ORDEM_SERVICO }

    public record Linha(long id, Origem origem, String referencia, String descricao, OffsetDateTime criadoEm,
                        String valor, String situacao) {}

    private static final MilionesimosConverter ESCALA = new MilionesimosConverter();
    private static final String LISTAGEM = """
            WITH notas AS (
                SELECT f.movimentacao_id id, 'ESTACIONAMENTO' origem,
                       coalesce(os.numero, 'Estadia ' || f.movimentacao_id) referencia,
                       concat_ws(' · ', v.placa, e.nome, p.nome) descricao,
                       f.criado_em, f.valor_micros, f.status situacao
                FROM patio_evento_fiscal f
                JOIN patio_evento_estadia s ON s.organizacao_id=f.organizacao_id AND s.movimentacao_id=f.movimentacao_id
                JOIN movimentacao m ON m.organizacao_id=f.organizacao_id AND m.id=f.movimentacao_id
                JOIN veiculo v ON v.organizacao_id=f.organizacao_id AND v.id=m.veiculo_id
                JOIN evento e ON e.organizacao_id=f.organizacao_id AND e.id=s.evento_id
                JOIN patio p ON p.organizacao_id=f.organizacao_id AND p.id=s.patio_id
                LEFT JOIN ordem_servico os ON os.organizacao_id=f.organizacao_id
                    AND os.movimentacao_patio_id=f.movimentacao_id AND os.id=f.ordem_servico_id
                WHERE f.organizacao_id=:org AND f.status='PENDENTE_EMISSAO'
                  AND cast(:cliente AS bigint) IS NULL
                UNION ALL
                SELECT n.id, CASE WHEN n.ordem_servico_id IS NULL THEN 'MANUAL' ELSE 'ORDEM_SERVICO' END,
                       n.numero, concat_ws(' · ', c.nome, os.numero),
                       n.created_at, n.valor_total_micros, n.situacao
                FROM nota_servico n
                LEFT JOIN cliente c ON c.organizacao_id=n.organizacao_id AND c.id=n.cliente_id
                LEFT JOIN ordem_servico os ON os.organizacao_id=n.organizacao_id AND os.id=n.ordem_servico_id
                WHERE n.organizacao_id=:org
                  AND (cast(:cliente AS bigint) IS NULL OR n.cliente_id=:cliente)
            )
            """;
    private static final String FILTROS = """
            FROM notas
            WHERE (:origem='' OR origem=:origem)
              AND (:situacao='' OR situacao=:situacao
                   OR (:situacao='PENDENTE' AND situacao='PENDENTE_EMISSAO'))
              AND (:busca='' OR strpos(lower(referencia),lower(:busca))>0
                             OR strpos(lower(descricao),lower(:busca))>0)
            """;
    private final NamedParameterJdbcTemplate jdbc;

    NotaServicoListagemService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Linha> listar(long org, String busca, Origem origem, SituacaoNotaServico situacao,
                              Long clienteId, Pageable pagina) {
        var parametros = new MapSqlParameterSource()
                .addValue("org", org)
                .addValue("busca", busca)
                .addValue("origem", origem == null ? "" : origem.name())
                .addValue("situacao", situacao == null ? "" : situacao.name())
                .addValue("cliente", clienteId)
                .addValue("limite", pagina.getPageSize())
                .addValue("deslocamento", pagina.getOffset());
        long total = jdbc.queryForObject(LISTAGEM + "SELECT count(*)\n" + FILTROS, parametros, Long.class);
        if (total == 0) return Page.empty(pagina);
        var linhas = jdbc.query(LISTAGEM + "SELECT *\n" + FILTROS + """
                ORDER BY criado_em DESC, origem, id DESC
                LIMIT :limite OFFSET :deslocamento
                """, parametros, (rs, numero) -> new Linha(rs.getLong("id"), Origem.valueOf(rs.getString("origem")),
                rs.getString("referencia"), rs.getString("descricao"), rs.getObject("criado_em", OffsetDateTime.class),
                ESCALA.convertToEntityAttribute(rs.getBigDecimal("valor_micros")).toPlainString(),
                rs.getString("situacao")));
        return new PageImpl<>(linhas, pagina, total);
    }
}
