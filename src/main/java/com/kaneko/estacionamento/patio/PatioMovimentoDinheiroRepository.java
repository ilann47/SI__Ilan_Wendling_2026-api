package com.kaneko.estacionamento.patio;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatioMovimentoDinheiroRepository {
    private static final MilionesimosConverter ESCALA=new MilionesimosConverter();
    private final JdbcTemplate jdbc;
    PatioMovimentoDinheiroRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    record Totais(BigDecimal suprimentos,BigDecimal sangrias){}
    public record Movimento(long id,UUID chave,PatioMovimentoDinheiroService.Tipo tipo,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valor,String motivo,OffsetDateTime registradoEm,long usuarioId,long turnoId){}
    record TurnoOperador(PatioCaixaRepository.Turno turno,String operador){}
    private static Movimento movimento(java.sql.ResultSet r,int row)throws java.sql.SQLException{
        return new Movimento(r.getLong("id"),r.getObject("chave",UUID.class),
            PatioMovimentoDinheiroService.Tipo.valueOf(r.getString("tipo")),ESCALA.convertToEntityAttribute(r.getBigDecimal("valor_micros")),
            r.getString("motivo"),r.getObject("registrado_em",OffsetDateTime.class),r.getLong("usuario_id"),r.getLong("turno_id"));
    }
    Totais totais(long org,long turno){
        return jdbc.queryForObject("""
            SELECT coalesce(sum(valor_micros) FILTER(WHERE tipo='SUPRIMENTO'),0) suprimentos,
                   coalesce(sum(valor_micros) FILTER(WHERE tipo='SANGRIA'),0) sangrias
            FROM patio_movimento_dinheiro WHERE organizacao_id=? AND turno_id=?
            """,(r,n)->new Totais(ESCALA.convertToEntityAttribute(r.getBigDecimal("suprimentos")),
                ESCALA.convertToEntityAttribute(r.getBigDecimal("sangrias"))),org,turno);
    }
    void bloquearChave(long org,UUID chave){
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",r->{}, "patio-dinheiro:"+org+":"+chave);
    }
    Optional<Movimento> porChave(long org,UUID chave){
        return jdbc.query("SELECT * FROM patio_movimento_dinheiro WHERE organizacao_id=? AND chave=?",
            PatioMovimentoDinheiroRepository::movimento,org,chave).stream().findFirst();
    }
    Movimento inserir(long org,long turno,long usuario,UUID chave,PatioMovimentoDinheiroService.Tipo tipo,BigDecimal valor,String motivo){
        return jdbc.queryForObject("""
            INSERT INTO patio_movimento_dinheiro(organizacao_id,turno_id,usuario_id,chave,tipo,valor_micros,motivo)
            VALUES(?,?,?,?,?,?,?) RETURNING *
            """,PatioMovimentoDinheiroRepository::movimento,org,turno,usuario,chave,tipo.name(),ESCALA.convertToDatabaseColumn(valor),motivo);
    }
    List<Movimento> historico(long org,long turno){
        return jdbc.query("SELECT * FROM patio_movimento_dinheiro WHERE organizacao_id=? AND turno_id=? ORDER BY id",
            PatioMovimentoDinheiroRepository::movimento,org,turno);
    }
    Optional<PatioCaixaRepository.Turno> turno(long org,long turno){
        return jdbc.query("SELECT * FROM patio_turno WHERE organizacao_id=? AND id=?",PatioCaixaRepository::turno,org,turno).stream().findFirst();
    }
    Optional<PatioCaixaRepository.Turno> turnoDoOperador(long org,long ator,long turno){
        return jdbc.query("SELECT * FROM patio_turno WHERE organizacao_id=? AND operador_id=? AND id=?",
            PatioCaixaRepository::turno,org,ator,turno).stream().findFirst();
    }
    List<TurnoOperador> turnos(long org,int pagina,int tamanho){
        return jdbc.query("""
            SELECT t.*,coalesce(nullif(trim(u.nome),''),u.login) operador FROM patio_turno t JOIN usuario u ON u.id=t.operador_id
            WHERE t.organizacao_id=? ORDER BY t.id DESC LIMIT ? OFFSET ?
            """,(r,n)->new TurnoOperador(PatioCaixaRepository.turno(r,n),r.getString("operador")),org,tamanho,pagina*tamanho);
    }
    long quantidade(long org){return jdbc.queryForObject("SELECT count(*) FROM patio_turno WHERE organizacao_id=?",Long.class,org);}
}
