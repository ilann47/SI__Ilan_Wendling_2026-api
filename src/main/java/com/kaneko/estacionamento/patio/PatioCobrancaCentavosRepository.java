package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatioCobrancaCentavosRepository {
    private final JdbcTemplate jdbc;
    private static final MilionesimosConverter ESCALA=new MilionesimosConverter();
    PatioCobrancaCentavosRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    void marcarComum(long org,long mov){
        jdbc.update("INSERT INTO patio_cobranca_centavos(organizacao_id,movimentacao_id,fase) VALUES(?,?,'SAIDA')",org,mov);
    }
    void evento(long org,long mov,PatioEventoService.Modo modo,PatioCobrancaCentavos.Valor valor){
        jdbc.update("""
            INSERT INTO patio_cobranca_centavos(organizacao_id,movimentacao_id,fase,valor_calculado_micros,
              valor_devido_micros,diferenca_micros,confirmado_em) VALUES(?,?,?,?,?,?,?)
            """,org,mov,modo.name(),ESCALA.convertToDatabaseColumn(valor.valorCalculado()),
            ESCALA.convertToDatabaseColumn(valor.valorDevido()),ESCALA.convertToDatabaseColumn(valor.diferencaArredondamento()),OffsetDateTime.now());
    }
    boolean novaRegra(long org,long mov){
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM patio_cobranca_centavos WHERE organizacao_id=? AND movimentacao_id=?)",
            Boolean.class,org,mov);
    }
    Optional<PatioCobrancaCentavos.Valor> snapshot(long org,long mov){
        return jdbc.query("""
            SELECT * FROM patio_cobranca_centavos WHERE organizacao_id=? AND movimentacao_id=? AND valor_calculado_micros IS NOT NULL
            """,(r,n)->valor(r),org,mov).stream().findFirst();
    }
    void confirmarComum(long org,long mov,PatioCobrancaCentavos.Valor valor){
        jdbc.update("""
            UPDATE patio_cobranca_centavos SET valor_calculado_micros=?,valor_devido_micros=?,diferenca_micros=?,confirmado_em=?
            WHERE organizacao_id=? AND movimentacao_id=? AND confirmado_em IS NULL
            """,ESCALA.convertToDatabaseColumn(valor.valorCalculado()),ESCALA.convertToDatabaseColumn(valor.valorDevido()),
            ESCALA.convertToDatabaseColumn(valor.diferencaArredondamento()),OffsetDateTime.now(),org,mov);
    }
    static PatioCobrancaCentavos.Valor valor(ResultSet r)throws SQLException{
        return new PatioCobrancaCentavos.Valor(ESCALA.convertToEntityAttribute(r.getBigDecimal("valor_calculado_micros")),
            ESCALA.convertToEntityAttribute(r.getBigDecimal("valor_devido_micros")),ESCALA.convertToEntityAttribute(r.getBigDecimal("diferenca_micros")));
    }
}
