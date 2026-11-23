package com.kaneko.estacionamento.patio;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.evento.VersaoEventoDivergenteException;
import com.kaneko.estacionamento.evento.dto.ChecklistConfiguracaoEventoResponse;
import com.kaneko.estacionamento.evento.dto.EventoResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preparação avulsa e conciliação de fontes reais; nunca transmite NFS-e ou cria recebíveis. */
@Service
public class PatioEventoOperacaoService {
    private static final MilionesimosConverter ESCALA = new MilionesimosConverter();
    private final EventoRepository eventos;
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public record Preparacao(boolean localAtivo, boolean alocacao, boolean cobranca,
                             boolean politica, boolean semVendaAntecipada, boolean pronto,
                             List<String> pendencias) {}
    public record Operacao(long entradas, long abertos, long encerrados, long cancelados,
                           long ocupacaoCredenciais) {}
    public record Financeiro(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dinheiro,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pix,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cartao,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
            long turnosAbertos, long turnosFechados, long turnosMistos,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal diferencaDinheiro) {}
    public record Pendencia(long movimentacaoId, String placa, long ordemServicoId, String ordemServicoNumero,
                            String status, OffsetDateTime criadoEm) {}
    public record Fiscal(long pendentes, List<Pendencia> itens, boolean listaLimitada) {}
    public record Resumo(long eventoId, String nome, StatusEvento status, Preparacao preparacao,
                         Operacao operacao, Financeiro financeiro, Fiscal fiscal) {}

    PatioEventoOperacaoService(EventoRepository eventos, JdbcTemplate jdbc,
                               AuditoriaService auditoria, OutboxService outbox) {
        this.eventos = eventos;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Resumo resumo(long org, long id, boolean financeiro, boolean fiscal) {
        var evento = eventos.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", id));
        var operacao = jdbc.queryForObject("""
                SELECT count(*) entradas, count(*) FILTER (WHERE m.status='ABERTO') abertos,
                       count(*) FILTER (WHERE m.status='FECHADO') encerrados,
                       count(*) FILTER (WHERE m.status='CANCELADO') cancelados
                FROM patio_evento_estadia e JOIN movimentacao m ON m.id=e.movimentacao_id AND m.organizacao_id=e.organizacao_id
                WHERE e.organizacao_id=? AND e.evento_id=?
                """, (r, n) -> new Operacao(r.getLong("entradas"), r.getLong("abertos"), r.getLong("encerrados"),
                    r.getLong("cancelados"), 0), org, id);
        long credenciais = jdbc.queryForObject("""
                SELECT coalesce(sum(a.ocupacao_atual),0) -
                    (SELECT count(*) FROM patio_evento_estadia s JOIN movimentacao m ON m.id=s.movimentacao_id
                     WHERE s.organizacao_id=? AND s.evento_id=? AND m.status='ABERTO')
                FROM alocacao_patio_evento a WHERE a.organizacao_id=? AND a.evento_id=?
                """, Long.class, org, id, org, id);
        return new Resumo(id, evento.getNome(), evento.getStatus(), preparacao(evento),
                new Operacao(operacao.entradas(), operacao.abertos(), operacao.encerrados(), operacao.cancelados(), credenciais),
                financeiro ? financeiro(org, id) : null, fiscal ? fiscal(org, id) : null);
    }

    @Transactional
    public EventoResponse iniciar(long org, long id, long versao) {
        Evento evento = eventos.findByIdAndOrganizacaoIdForUpdate(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", id));
        if (evento.getVersion() != versao) throw new VersaoEventoDivergenteException();
        var preparo = preparacao(evento);
        if (!preparo.pronto()) throw new ConflitoException(String.join(" ", preparo.pendencias()));
        var agora = OffsetDateTime.now();
        if (agora.isBefore(evento.getInicio()) || !agora.isBefore(evento.getFim()))
            throw new ConflitoException("A operação só pode começar durante o período do evento.");
        boolean janela = jdbc.queryForObject("""
                SELECT count(*)>0 FROM alocacao_patio_evento a JOIN patio p ON p.id=a.patio_id AND p.organizacao_id=a.organizacao_id
                WHERE a.organizacao_id=? AND a.evento_id=? AND p.ativo=true AND a.inicio<=? AND a.fim>?
                  AND a.capacidade_operacional>a.capacidade_vendavel+a.capacidade_reservada
                """, Boolean.class, org, id, agora, agora);
        if (!janela) throw new ConflitoException("Nenhum pátio está na janela de operação deste evento.");
        var anterior = evento.getStatus();
        evento.iniciarOperacaoAvulsa();
        eventos.flush();
        var dados = Map.<String, Object>of("status", evento.getStatus().name(), "modoOperacao", "AVULSO",
                "estadoAnterior", anterior.name());
        auditoria.registrar(new RegistrarAuditoriaCommand(org, "OPERACAO_AVULSA_INICIADA", "EVENTO", id, id,
                null, null, null, dados, versao, evento.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand("OperacaoAvulsaDoEventoIniciada", org, "EVENTO", id,
                evento.getVersion(), null, 1, dados));
        return EventoResponse.from(evento,
                new ChecklistConfiguracaoEventoResponse(true, true, true, false, true, true));
    }

    private Preparacao preparacao(Evento evento) {
        long org = evento.getOrganizacaoId(), id = evento.getId();
        boolean local = jdbc.queryForObject("SELECT count(*)>0 FROM unidade_operacional WHERE id=? AND organizacao_id=? AND ativo=true",
                Boolean.class, evento.getVenueId(), org);
        // Não exige janela vigente ao preparar; ao iniciar a janela do evento e ao menos uma alocação devem estar vigentes.
        boolean alocacao = jdbc.queryForObject("""
                SELECT count(*)>0 FROM alocacao_patio_evento a JOIN patio p ON p.id=a.patio_id AND p.organizacao_id=a.organizacao_id
                WHERE a.organizacao_id=? AND a.evento_id=? AND p.ativo=true
                  AND a.capacidade_operacional>a.capacidade_vendavel+a.capacidade_reservada
                """, Boolean.class, org, id);
        boolean cobranca = jdbc.queryForObject("SELECT count(*)>0 FROM patio_evento_cobranca WHERE organizacao_id=? AND evento_id=? AND valor_micros>=5000",
                Boolean.class, org, id);
        boolean semProduto = jdbc.queryForObject("SELECT count(*)=0 FROM produto_estacionamento WHERE organizacao_id=? AND evento_id=?",
                Boolean.class, org, id);
        var pendencias = new ArrayList<String>();
        if (!local) pendencias.add("Cadastre um local de evento ativo.");
        if (!alocacao) pendencias.add("Vincule um pátio ativo com capacidade para entradas sem reserva.");
        if (!cobranca) pendencias.add("Defina o preço e o momento da cobrança.");
        if (evento.getPoliticaReentrada() == null) pendencias.add("Defina a política de reentrada.");
        if (!semProduto) pendencias.add("Este evento tem venda antecipada. Use o ciclo de publicação e vendas.");
        var agora = OffsetDateTime.now();
        if (!agora.isBefore(evento.getFim())) pendencias.add("O período do evento já terminou.");
        boolean estado = evento.getStatus() == StatusEvento.RASCUNHO || evento.getStatus() == StatusEvento.CONFIGURADO;
        if (!estado && evento.getStatus() != StatusEvento.EM_OPERACAO)
            pendencias.add("O estado atual não permite iniciar operação avulsa.");
        return new Preparacao(local, alocacao, cobranca, evento.getPoliticaReentrada() != null, semProduto,
                pendencias.isEmpty() && estado, List.copyOf(pendencias));
    }

    private static final String RECEBIMENTOS = """
            WITH recebimentos AS (
                SELECT e.movimentacao_id, e.turno_recebimento_entrada_id turno_id, e.meio_entrada meio, e.valor_micros
                FROM patio_evento_estadia e WHERE e.organizacao_id=? AND e.evento_id=? AND e.meio_entrada IS NOT NULL
                UNION ALL
                SELECT e.movimentacao_id, c.turno_id, c.meio, c.valor_micros FROM patio_evento_estadia e
                JOIN patio_checkout c ON c.movimentacao_id=e.movimentacao_id AND c.organizacao_id=e.organizacao_id
                WHERE e.organizacao_id=? AND e.evento_id=? AND c.confirmado_em IS NOT NULL
                UNION ALL
                SELECT e.movimentacao_id, a.turno_id, a.meio, a.valor_micros FROM patio_evento_estadia e
                JOIN patio_operacao_ajuste a ON a.movimentacao_id=e.movimentacao_id AND a.organizacao_id=e.organizacao_id
                WHERE e.organizacao_id=? AND e.evento_id=?
            )
            """;

    private Financeiro financeiro(long org, long id) {
        BigDecimal[] valores = jdbc.queryForObject(RECEBIMENTOS + """
                SELECT coalesce(sum(valor_micros) FILTER (WHERE meio='DINHEIRO'),0) dinheiro,
                       coalesce(sum(valor_micros) FILTER (WHERE meio='PIX'),0) pix,
                       coalesce(sum(valor_micros) FILTER (WHERE meio='CARTAO'),0) cartao,
                       coalesce(sum(valor_micros),0) total
                FROM recebimentos
                """, (r, n) -> new BigDecimal[] { micros(r.getBigDecimal("dinheiro")), micros(r.getBigDecimal("pix")),
                        micros(r.getBigDecimal("cartao")), micros(r.getBigDecimal("total")) }, org, id, org, id, org, id);
        // Totais de turno são de toda a conferência, não uma diferença atribuída artificialmente ao evento.
        long[] turnos = jdbc.queryForObject(RECEBIMENTOS + """
                SELECT count(*) FILTER (WHERE t.fechado_em IS NULL) abertos,
                       count(*) FILTER (WHERE t.fechado_em IS NOT NULL) fechados,
                       count(*) FILTER (WHERE EXISTS (
                         SELECT 1 FROM movimentacao m LEFT JOIN patio_evento_estadia e ON e.movimentacao_id=m.id
                         WHERE m.organizacao_id=t.organizacao_id AND m.patio_turno_entrada_id=t.id
                           AND (e.evento_id IS NULL OR e.evento_id<>?)
                       ) OR EXISTS (
                         SELECT 1 FROM patio_checkout c LEFT JOIN patio_evento_estadia e ON e.movimentacao_id=c.movimentacao_id
                         WHERE c.organizacao_id=t.organizacao_id AND c.turno_id=t.id AND c.confirmado_em IS NOT NULL
                           AND (e.evento_id IS NULL OR e.evento_id<>?)
                       ) OR EXISTS (
                         SELECT 1 FROM patio_operacao_ajuste a LEFT JOIN patio_evento_estadia e ON e.movimentacao_id=a.movimentacao_id
                         WHERE a.organizacao_id=t.organizacao_id AND a.turno_id=t.id
                           AND (e.evento_id IS NULL OR e.evento_id<>?)
                       )) mistos
                FROM patio_turno t WHERE t.organizacao_id=? AND t.id IN (SELECT DISTINCT turno_id FROM recebimentos)
                """, (r, n) -> new long[] { r.getLong("abertos"), r.getLong("fechados"), r.getLong("mistos") },
                org, id, org, id, org, id, id, id, id, org);
        // Soma da diferença real de cada turno fechado envolvido. Leituras por evento não duplicam o turno.
        BigDecimal diferenca = jdbc.queryForObject(RECEBIMENTOS + """
                SELECT coalesce(sum(t.dinheiro_contado_micros - t.fundo_troco_micros -
                   (SELECT coalesce(sum(x.valor_micros),0) FROM (
                     SELECT c.valor_micros FROM patio_checkout c WHERE c.organizacao_id=t.organizacao_id AND c.turno_id=t.id
                       AND c.confirmado_em IS NOT NULL AND c.meio='DINHEIRO'
                     UNION ALL SELECT e.valor_micros FROM patio_evento_estadia e WHERE e.organizacao_id=t.organizacao_id
                       AND e.turno_recebimento_entrada_id=t.id AND e.meio_entrada='DINHEIRO'
                     UNION ALL SELECT a.valor_micros FROM patio_operacao_ajuste a WHERE a.organizacao_id=t.organizacao_id
                       AND a.turno_id=t.id AND a.meio='DINHEIRO'
                     UNION ALL SELECT CASE WHEN d.tipo='SUPRIMENTO' THEN d.valor_micros ELSE -d.valor_micros END
                       FROM patio_movimento_dinheiro d WHERE d.organizacao_id=t.organizacao_id AND d.turno_id=t.id) x)),0)
                FROM patio_turno t WHERE t.organizacao_id=? AND t.fechado_em IS NOT NULL
                  AND t.id IN (SELECT DISTINCT turno_id FROM recebimentos)
                """, BigDecimal.class, org, id, org, id, org, id, org);
        return new Financeiro(valores[0], valores[1], valores[2], valores[3], turnos[0], turnos[1], turnos[2], micros(diferenca));
    }

    private Fiscal fiscal(long org, long id) {
        long quantidade = jdbc.queryForObject("""
                SELECT count(*) FROM patio_evento_fiscal f JOIN patio_evento_estadia e ON e.movimentacao_id=f.movimentacao_id
                WHERE f.organizacao_id=? AND e.evento_id=? AND f.status='PENDENTE_EMISSAO'
                """, Long.class, org, id);
        var itens = jdbc.query("""
                SELECT f.movimentacao_id,v.placa,os.id os_id,os.numero,f.status,f.criado_em
                FROM patio_evento_fiscal f JOIN patio_evento_estadia e ON e.movimentacao_id=f.movimentacao_id AND e.organizacao_id=f.organizacao_id
                JOIN movimentacao m ON m.id=f.movimentacao_id JOIN veiculo v ON v.id=m.veiculo_id
                JOIN ordem_servico os ON os.id=f.ordem_servico_id AND os.organizacao_id=f.organizacao_id
                WHERE f.organizacao_id=? AND e.evento_id=? AND f.status='PENDENTE_EMISSAO'
                ORDER BY f.criado_em,f.movimentacao_id LIMIT 100
                """, (r, n) -> new Pendencia(r.getLong("movimentacao_id"), r.getString("placa"), r.getLong("os_id"),
                    r.getString("numero"), r.getString("status"), r.getObject("criado_em", OffsetDateTime.class)), org, id);
        return new Fiscal(quantidade, itens, quantidade > itens.size());
    }

    private static BigDecimal micros(BigDecimal valor) {
        return ESCALA.convertToEntityAttribute(valor);
    }
}
