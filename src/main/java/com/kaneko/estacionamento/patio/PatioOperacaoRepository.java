package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatioOperacaoRepository {
    private static final MilionesimosConverter ESCALA = new MilionesimosConverter();
    private final JdbcTemplate jdbc;
    PatioOperacaoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    record Pagamento(long turno, PatioCaixaService.Meio meio, BigDecimal valor, OffsetDateTime instante,
                     String fase, BigDecimal recebido, BigDecimal troco) {}
    record Correcao(long id, long movimento, String acao, String motivo, String placa, String meio) {}
    record VeiculoAberto(long movimentacaoId, String placa, Long patioId, String patioNome, Long eventoId, String eventoNome) {}

    void dinheiro(long org, long mov, String fase, long turno, PatioDinheiro.Valores valores) {
        if (valores.recebido() == null) return;
        jdbc.update("""
                INSERT INTO patio_pagamento_dinheiro(organizacao_id,movimentacao_id,fase,turno_id,recebido_micros,troco_micros)
                VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, org, mov, fase, turno, micros(valores.recebido()), micros(valores.troco()));
    }

    Optional<Pagamento> pagamento(long org, long mov) {
        return jdbc.query("""
                SELECT p.*,d.recebido_micros,d.troco_micros
                FROM (
                    SELECT turno_recebimento_entrada_id turno,meio_entrada meio,valor_micros,data_entrada instante,'ENTRADA' fase
                    FROM patio_evento_estadia s JOIN movimentacao m ON m.id=s.movimentacao_id
                    WHERE s.organizacao_id=? AND s.movimentacao_id=? AND s.meio_entrada IS NOT NULL
                    UNION ALL
                    SELECT turno_id,meio,valor_micros,confirmado_em,'SAIDA'
                    FROM patio_checkout WHERE organizacao_id=? AND movimentacao_id=? AND confirmado_em IS NOT NULL
                ) p LEFT JOIN patio_pagamento_dinheiro d ON d.organizacao_id=? AND d.movimentacao_id=? AND d.fase=p.fase
                ORDER BY p.valor_micros DESC LIMIT 1
                """, (rs,n) -> new Pagamento(rs.getLong("turno"), PatioCaixaService.Meio.valueOf(rs.getString("meio")),
                    valor(rs.getBigDecimal("valor_micros")), rs.getObject("instante",OffsetDateTime.class), rs.getString("fase"),
                    valor(rs.getBigDecimal("recebido_micros")), valor(rs.getBigDecimal("troco_micros"))),
                org,mov,org,mov,org,mov).stream().findFirst();
    }

    PatioCaixaService.Meio meioEfetivo(long org, long mov, PatioCaixaService.Meio original) {
        return jdbc.query("""
                SELECT meio_novo FROM patio_operacao_correcao WHERE organizacao_id=? AND movimentacao_id=?
                AND acao='CORRIGIR_MEIO' ORDER BY id DESC LIMIT 1
                """, (rs,n) -> PatioCaixaService.Meio.valueOf(rs.getString(1)), org,mov)
                .stream().findFirst().orElse(original);
    }

    List<Long> comprovantes(long org, String placa) {
        String busca = PatioPlaca.normalizar(placa);
        return jdbc.query("""
                SELECT m.id FROM movimentacao m JOIN veiculo v ON v.id=m.veiculo_id
                WHERE m.organizacao_id=? AND (? = '' OR strpos(%s, ?) > 0)
                AND (EXISTS(SELECT 1 FROM patio_evento_estadia s WHERE s.movimentacao_id=m.id AND s.meio_entrada IS NOT NULL)
                  OR EXISTS(SELECT 1 FROM patio_checkout c WHERE c.movimentacao_id=m.id AND c.confirmado_em IS NOT NULL))
                ORDER BY m.id DESC LIMIT 50
                """.formatted(PatioPlaca.SQL_BUSCA), (rs,n)->rs.getLong(1), org,busca,busca);
    }

    List<VeiculoAberto> buscar(long org, String placa, Long patio, Long evento) {
        String busca = PatioPlaca.normalizar(placa);
        if (busca.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT m.id,v.placa,m.patio_id,p.nome patio_nome,s.evento_id,e.nome evento_nome
                FROM movimentacao m JOIN veiculo v ON v.id=m.veiculo_id
                LEFT JOIN patio p ON p.id=m.patio_id
                LEFT JOIN patio_evento_estadia s ON s.movimentacao_id=m.id
                LEFT JOIN evento e ON e.id=s.evento_id
                WHERE m.organizacao_id=? AND m.status='ABERTO' AND strpos(%s, ?) > 0
                    AND (?::bigint IS NULL OR m.patio_id=?) AND (?::bigint IS NULL OR s.evento_id=?)
                ORDER BY (%s=?) DESC,m.data_entrada LIMIT 20
                """.formatted(PatioPlaca.SQL_BUSCA, PatioPlaca.SQL_BUSCA),
                (rs,n)->new VeiculoAberto(rs.getLong("id"),rs.getString("placa"),rs.getObject("patio_id",Long.class),
                    rs.getString("patio_nome"),rs.getObject("evento_id",Long.class),rs.getString("evento_nome")),
                    org,busca,patio,patio,evento,evento,busca);
    }

    void bloquearChave(long org, UUID chave) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{}, "patio-correcao:"+org+":"+chave);
    }
    Optional<Correcao> correcao(long org, UUID chave) {
        return jdbc.query("SELECT * FROM patio_operacao_correcao WHERE organizacao_id=? AND chave=?",
            (rs,n)->new Correcao(rs.getLong("id"),rs.getLong("movimentacao_id"),rs.getString("acao"),rs.getString("motivo"),
                rs.getString("placa_nova"),rs.getString("meio_novo")),org,chave).stream().findFirst();
    }
    long registrar(long org,long mov,long ator,UUID chave,String acao,String motivo,String anterior,String nova,PatioCaixaService.Meio meio) {
        return jdbc.queryForObject("""
                INSERT INTO patio_operacao_correcao(organizacao_id,movimentacao_id,usuario_id,chave,acao,motivo,
                placa_anterior,placa_nova,meio_novo,criado_em) VALUES(?,?,?,?,?,?,?,?,?,now()) RETURNING id
                """,Long.class,org,mov,ator,chave,acao,motivo,anterior,nova,meio==null?null:meio.name());
    }
    void lancar(long org,long mov,long turno,long correcao,PatioCaixaService.Meio meio,BigDecimal valor) {
        if(valor.signum()==0)return;
        jdbc.update("""
                INSERT INTO patio_operacao_ajuste(organizacao_id,movimentacao_id,turno_id,correcao_id,meio,valor_micros)
                VALUES(?,?,?,?,?,?)
                """,org,mov,turno,correcao,meio.name(),micros(valor));
    }
    boolean fiscalCancelavel(long org,long mov) {
        return jdbc.queryForObject("""
                SELECT NOT EXISTS(SELECT 1 FROM patio_evento_fiscal WHERE organizacao_id=? AND movimentacao_id=?
                  AND status NOT IN ('PENDENTE_EMISSAO','CANCELADA'))
                """,Boolean.class,org,mov);
    }
    void cancelarFiscal(long org,long mov) {
        jdbc.update("UPDATE patio_evento_fiscal SET status='CANCELADA', versao=versao+1 WHERE organizacao_id=? AND movimentacao_id=? AND status='PENDENTE_EMISSAO'",org,mov);
    }
    private static BigDecimal micros(BigDecimal v){return ESCALA.convertToDatabaseColumn(v);}
    private static BigDecimal valor(BigDecimal v){return ESCALA.convertToEntityAttribute(v);}
}
