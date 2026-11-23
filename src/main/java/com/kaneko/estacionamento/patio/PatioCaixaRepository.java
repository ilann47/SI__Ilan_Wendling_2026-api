package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatioCaixaRepository {
    private final JdbcTemplate jdbc;
    private static final MilionesimosConverter ESCALA = new MilionesimosConverter();

    record Turno(long id, OffsetDateTime abertoEm, OffsetDateTime fechadoEm,
                 BigDecimal fundoTroco, BigDecimal dinheiroContado) {}
    record Checkout(long turnoId, UUID chave, OffsetDateTime calculadoEm, OffsetDateTime expiraEm,
                    BigDecimal valor, OffsetDateTime confirmadoEm, PatioCaixaService.Meio meio,
                    BigDecimal valorCalculado) {
        Checkout(long turnoId,UUID chave,OffsetDateTime calculadoEm,OffsetDateTime expiraEm,BigDecimal valor,
                 OffsetDateTime confirmadoEm,PatioCaixaService.Meio meio){
            this(turnoId,chave,calculadoEm,expiraEm,valor,confirmadoEm,meio,null);
        }
    }
    record Totais(BigDecimal dinheiro, BigDecimal pix, BigDecimal cartao, long saidas) {}

    PatioCaixaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Turno> aberto(long org, long operador, boolean bloquear) {
        return jdbc.query("""
                SELECT * FROM patio_turno WHERE organizacao_id=? AND operador_id=? AND fechado_em IS NULL
                """ + (bloquear ? " FOR UPDATE" : ""), PatioCaixaRepository::turno, org, operador).stream().findFirst();
    }

    Optional<Turno> ultimoFechado(long org, long operador) {
        return jdbc.query("""
                SELECT * FROM patio_turno WHERE organizacao_id=? AND operador_id=? AND fechado_em IS NOT NULL
                ORDER BY id DESC LIMIT 1
                """, PatioCaixaRepository::turno, org, operador).stream().findFirst();
    }

    Optional<Turno> bloquear(long org, long operador, long id) {
        return jdbc.query("SELECT * FROM patio_turno WHERE organizacao_id=? AND operador_id=? AND id=? FOR UPDATE",
                PatioCaixaRepository::turno, org, operador, id).stream().findFirst();
    }

    Optional<Turno> abrir(long org, long operador, OffsetDateTime instante, BigDecimal fundo) {
        return jdbc.query("""
                INSERT INTO patio_turno (organizacao_id, operador_id, aberto_em, fundo_troco_micros)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING *
                """, PatioCaixaRepository::turno, org, operador, instante, micros(fundo)).stream().findFirst();
    }

    Turno fechar(Turno turno, OffsetDateTime instante, BigDecimal contado) {
        return jdbc.queryForObject("""
                UPDATE patio_turno SET fechado_em=?, dinheiro_contado_micros=? WHERE id=? RETURNING *
                """, PatioCaixaRepository::turno, instante, micros(contado), turno.id());
    }

    Optional<Checkout> checkout(long org, long movimento) {
        return jdbc.query("""
                SELECT * FROM patio_checkout WHERE organizacao_id=? AND movimentacao_id=?
                """, (rs, row) -> new Checkout(rs.getLong("turno_id"), rs.getObject("chave", UUID.class),
                instante(rs, "calculado_em"), instante(rs, "expira_em"), valor(rs, "valor_micros"),
                instante(rs, "confirmado_em"), rs.getString("meio") == null ? null
                        : PatioCaixaService.Meio.valueOf(rs.getString("meio")),valor(rs,"valor_calculado_micros")), org, movimento).stream().findFirst();
    }

    void salvarPrevia(long org, long movimento, Checkout checkout) {
        jdbc.update("""
                INSERT INTO patio_checkout
                    (organizacao_id, movimentacao_id, turno_id, chave, calculado_em, expira_em, valor_micros,valor_calculado_micros)
                VALUES (?, ?, ?, ?, ?, ?, ?,?)
                ON CONFLICT (movimentacao_id) DO UPDATE SET turno_id=excluded.turno_id,
                    chave=excluded.chave, calculado_em=excluded.calculado_em,
                    expira_em=excluded.expira_em, valor_micros=excluded.valor_micros,valor_calculado_micros=excluded.valor_calculado_micros
                """, org, movimento, checkout.turnoId(), checkout.chave(), checkout.calculadoEm(),
                checkout.expiraEm(), micros(checkout.valor()),micros(checkout.valorCalculado()));
    }

    void confirmar(long org, long movimento, OffsetDateTime instante, PatioCaixaService.Meio meio) {
        jdbc.update("""
                UPDATE patio_checkout SET confirmado_em=?, meio=? WHERE organizacao_id=? AND movimentacao_id=?
                """, instante, meio.name(), org, movimento);
    }

    Totais totais(long org, long turno) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(valor_micros) FILTER (WHERE meio='DINHEIRO'),0) AS dinheiro,
                       coalesce(sum(valor_micros) FILTER (WHERE meio='PIX'),0) AS pix,
                       coalesce(sum(valor_micros) FILTER (WHERE meio='CARTAO'),0) AS cartao,
                       count(*) FILTER (WHERE saida) AS saidas
                FROM (
                  SELECT valor_micros, meio, true saida
                  FROM patio_checkout WHERE organizacao_id=? AND turno_id=? AND confirmado_em IS NOT NULL
                  UNION ALL
                  SELECT valor_micros, meio_entrada, false
                  FROM patio_evento_estadia WHERE organizacao_id=? AND turno_recebimento_entrada_id=?
                  UNION ALL
                  SELECT valor_micros,meio,false FROM patio_operacao_ajuste WHERE organizacao_id=? AND turno_id=?
                ) recebimentos
                """, (rs, row) -> new Totais(valor(rs, "dinheiro"), valor(rs, "pix"),
                valor(rs, "cartao"), rs.getLong("saidas")), org, turno, org, turno,org,turno);
    }

    static Turno turno(ResultSet rs, int row) throws SQLException {
        return new Turno(rs.getLong("id"), instante(rs, "aberto_em"), instante(rs, "fechado_em"),
                valor(rs, "fundo_troco_micros"), valor(rs, "dinheiro_contado_micros"));
    }

    private static OffsetDateTime instante(ResultSet rs, String coluna) throws SQLException {
        return rs.getObject(coluna, OffsetDateTime.class);
    }

    private static BigDecimal valor(ResultSet rs, String coluna) throws SQLException {
        return ESCALA.convertToEntityAttribute(rs.getBigDecimal(coluna));
    }

    private static BigDecimal micros(BigDecimal valor) {
        return ESCALA.convertToDatabaseColumn(valor);
    }
}
