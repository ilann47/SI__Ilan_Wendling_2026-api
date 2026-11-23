package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import com.kaneko.estacionamento.ordensservico.StatusOrdemServico;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatioEventoRepository {
    private static final MilionesimosConverter ESCALA = new MilionesimosConverter();
    private static final String ESTADIAS = """
            SELECT s.*, e.nome, v.placa, m.data_entrada, m.status movimento_status, f.status fiscal_status, x.confirmado_em saida_confirmada,
                   os.id os_id, os.numero os_numero, os.status os_status,
                   cc.valor_calculado_micros,cc.valor_devido_micros,cc.diferenca_micros
            FROM patio_evento_estadia s JOIN evento e ON e.id=s.evento_id
            JOIN movimentacao m ON m.id=s.movimentacao_id
            JOIN veiculo v ON v.id=m.veiculo_id
            LEFT JOIN patio_evento_fiscal f ON f.movimentacao_id=s.movimentacao_id
            LEFT JOIN patio_checkout x ON x.movimentacao_id=s.movimentacao_id
            LEFT JOIN ordem_servico os ON os.organizacao_id=s.organizacao_id AND os.movimentacao_patio_id=s.movimentacao_id
            LEFT JOIN patio_cobranca_centavos cc ON cc.organizacao_id=s.organizacao_id AND cc.movimentacao_id=s.movimentacao_id
            WHERE s.organizacao_id=?
            """;
    private final JdbcTemplate jdbc;

    record Estadia(long id, long eventoId, long patioId, String nome, String placa,
                   PatioEventoService.Modo modo, BigDecimal valor, PatioCaixaService.Meio meioEntrada,
                   boolean saiu, String fiscalStatus, OffsetDateTime dataEntrada, Long ordemServicoId,
                   String ordemServicoNumero, StatusOrdemServico ordemServicoStatus, StatusMovimentacao movimentoStatus,
                   PatioCobrancaCentavos.Valor centavos, PatioTomador tomadorPagamento) {
        Estadia(long id,long eventoId,long patioId,String nome,String placa,PatioEventoService.Modo modo,BigDecimal valor,
            PatioCaixaService.Meio meioEntrada,boolean saiu,String fiscalStatus,OffsetDateTime dataEntrada,Long ordemServicoId,
            String ordemServicoNumero,StatusOrdemServico ordemServicoStatus,StatusMovimentacao movimentoStatus){
            this(id,eventoId,patioId,nome,placa,modo,valor,meioEntrada,saiu,fiscalStatus,dataEntrada,ordemServicoId,
                ordemServicoNumero,ordemServicoStatus,movimentoStatus,null,null);
        }
    }

    PatioEventoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<PatioEventoService.Cobranca> cobranca(long org, long evento) {
        return jdbc.query("""
                SELECT modo, valor_micros FROM patio_evento_cobranca WHERE organizacao_id=? AND evento_id=?
                """, (rs, n) -> new PatioEventoService.Cobranca(modo(rs), valor(rs)), org, evento).stream().findFirst();
    }

    void configurar(long org, long evento, PatioEventoService.Cobranca cobranca) {
        jdbc.update("""
                INSERT INTO patio_evento_cobranca (evento_id, organizacao_id, modo, valor_micros)
                VALUES (?,?,?,?)
                ON CONFLICT (evento_id) DO UPDATE SET modo=excluded.modo, valor_micros=excluded.valor_micros
                """, evento, org, cobranca.modo().name(), micros(cobranca.valor()));
    }

    List<PatioEventoService.Opcao> opcoes(long org, OffsetDateTime agora) {
        return jdbc.query("""
                SELECT e.id evento_id, e.nome, a.patio_id, p.nome patio_nome, c.modo, c.valor_micros
                FROM patio_evento_cobranca c JOIN evento e ON e.id=c.evento_id
                JOIN alocacao_patio_evento a ON a.evento_id=e.id
                JOIN patio p ON p.id=a.patio_id
                WHERE e.organizacao_id=? AND e.status='EM_OPERACAO' AND p.ativo=true
                  AND a.inicio<=? AND a.fim>? AND a.capacidade_operacional>a.capacidade_vendavel+a.capacidade_reservada
                ORDER BY e.nome,p.nome,e.id,p.id
                """, (rs, n) -> new PatioEventoService.Opcao(rs.getLong("evento_id"), rs.getString("nome"),
                rs.getLong("patio_id"), rs.getString("patio_nome"), modo(rs), valor(rs)), org, agora, agora);
    }

    long avulsosAbertos(long org, long evento, long patio) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM patio_evento_estadia s JOIN movimentacao m ON m.id=s.movimentacao_id
                WHERE s.organizacao_id=? AND s.evento_id=? AND s.patio_id=? AND m.status='ABERTO'
                """, Long.class, org, evento, patio);
    }

    long ocupacaoPorCredencial(long org, long patio) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(ocupacao_atual),0) -
                    (SELECT count(*) FROM patio_evento_estadia s JOIN movimentacao m ON m.id=s.movimentacao_id
                     WHERE s.organizacao_id=? AND s.patio_id=? AND m.status='ABERTO')
                FROM alocacao_patio_evento WHERE organizacao_id=? AND patio_id=?
                """, Long.class, org, patio, org, patio);
    }

    Optional<Estadia> porChave(long org, UUID chave) {
        return jdbc.query(ESTADIAS + " AND s.chave_entrada=?", PatioEventoRepository::estadia, org, chave)
                .stream().findFirst();
    }

    void bloquearChave(long org, UUID chave) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                rs -> { }, "patio-evento:" + org + ":" + chave);
    }

    Optional<Estadia> buscar(long org, long movimento) {
        return jdbc.query(ESTADIAS + " AND s.movimentacao_id=?", PatioEventoRepository::estadia, org, movimento)
                .stream().findFirst();
    }

    List<Estadia> abertas(long org) {
        return jdbc.query(ESTADIAS + " AND m.status='ABERTO' ORDER BY s.movimentacao_id",
                PatioEventoRepository::estadia, org);
    }

    void registrar(long org, long mov, long evento, long patio, UUID chave,
                   PatioEventoService.Cobranca cobranca, long turno, PatioCaixaService.Meio meio, PatioTomador tomador) {
        jdbc.update("""
                INSERT INTO patio_evento_estadia (movimentacao_id, organizacao_id, evento_id, patio_id,
                    chave_entrada, modo, valor_micros, turno_recebimento_entrada_id, meio_entrada,
                    tomador_pagamento_documento, tomador_pagamento_nome)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, mov, org, evento, patio, chave, cobranca.modo().name(), micros(cobranca.valor()),
                meio == null ? null : turno, meio == null ? null : meio.name(),
                tomador == null ? null : tomador.documento(), tomador == null ? null : tomador.nome());
    }

    void registrarTomadorNaSaida(long org, long mov, PatioTomador tomador) {
        jdbc.update("""
                UPDATE patio_evento_estadia SET tomador_pagamento_documento=?, tomador_pagamento_nome=?
                WHERE organizacao_id=? AND movimentacao_id=? AND modo='SAIDA'
                """, tomador.documento(), tomador.nome(), org, mov);
    }

    void criarPendenciaFiscal(long org, Estadia estadia, long ordemServicoId, OffsetDateTime instante) {
        jdbc.update("""
                INSERT INTO patio_evento_fiscal
                    (movimentacao_id, organizacao_id, status, valor_micros, criado_em, ordem_servico_id,
                     tomador_documento, tomador_nome)
                SELECT movimentacao_id, organizacao_id, 'PENDENTE_EMISSAO', valor_micros, ?, ?,
                       tomador_pagamento_documento, tomador_pagamento_nome
                FROM patio_evento_estadia WHERE organizacao_id=? AND movimentacao_id=?
                """, instante, ordemServicoId, org, estadia.id());
    }

    private static Estadia estadia(ResultSet rs, int row) throws SQLException {
        String meio = rs.getString("meio_entrada");
        String osStatus = rs.getString("os_status");
        return new Estadia(rs.getLong("movimentacao_id"), rs.getLong("evento_id"), rs.getLong("patio_id"),
                rs.getString("nome"), rs.getString("placa"), modo(rs), valor(rs),
                meio == null ? null : PatioCaixaService.Meio.valueOf(meio),
                rs.getObject("saida_confirmada") != null, rs.getString("fiscal_status"),
                rs.getObject("data_entrada", OffsetDateTime.class), rs.getObject("os_id", Long.class),
                rs.getString("os_numero"), osStatus == null ? null : StatusOrdemServico.valueOf(osStatus),
                StatusMovimentacao.valueOf(rs.getString("movimento_status")),
                rs.getBigDecimal("valor_calculado_micros")==null?null:PatioCobrancaCentavosRepository.valor(rs),
                PatioTomador.salvo(rs.getString("tomador_pagamento_documento"), rs.getString("tomador_pagamento_nome")));
    }

    private static PatioEventoService.Modo modo(ResultSet rs) throws SQLException {
        return PatioEventoService.Modo.valueOf(rs.getString("modo"));
    }

    private static BigDecimal valor(ResultSet rs) throws SQLException {
        return ESCALA.convertToEntityAttribute(rs.getBigDecimal("valor_micros"));
    }

    private static BigDecimal micros(BigDecimal valor) {
        return ESCALA.convertToDatabaseColumn(valor);
    }
}
