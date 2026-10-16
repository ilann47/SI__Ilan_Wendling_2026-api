package com.kaneko.estacionamento.inventario.alocacao;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.instalacoes.PatioInstalacao;
import com.kaneko.estacionamento.instalacoes.PatioInstalacaoRepository;
import com.kaneko.estacionamento.inventario.alocacao.dto.AlocacaoPatioEventoResponse;
import com.kaneko.estacionamento.inventario.alocacao.dto.AlterarCapacidadesAlocacaoRequest;
import com.kaneko.estacionamento.inventario.alocacao.dto.CriarAlocacaoPatioEventoRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlocacaoPatioEventoService {
    private static final String OPERACAO_CRIAR = "ALOCACAO_PATIO_EVENTO_CRIAR";
    private static final String RECURSO = "ALOCACAO_PATIO_EVENTO";

    private final AlocacaoPatioEventoRepository alocacoes;
    private final EventoRepository eventos;
    private final PatioInstalacaoRepository patios;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public AlocacaoPatioEventoService(
            AlocacaoPatioEventoRepository alocacoes, EventoRepository eventos,
            PatioInstalacaoRepository patios, IdempotenciaRepository idempotencia,
            AuditoriaService auditoria, OutboxService outbox) {
        this.alocacoes = alocacoes;
        this.eventos = eventos;
        this.patios = patios;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<AlocacaoPatioEventoResponse> listar(Long organizacaoId, Long eventoId) {
        eventos.findByIdAndOrganizacaoId(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        return alocacoes.findAllByOrganizacaoIdAndEventoIdOrderById(organizacaoId, eventoId)
                .stream().map(AlocacaoPatioEventoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AlocacaoPatioEventoResponse buscar(Long organizacaoId, Long alocacaoId) {
        return AlocacaoPatioEventoResponse.from(
                alocacoes.findByIdAndOrganizacaoId(alocacaoId, organizacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Alocacao do evento", alocacaoId)));
    }

    @Transactional
    public AlocacaoPatioEventoResponse criar(
            Long organizacaoId, Long atorId, Long eventoId,
            String chaveIdempotencia, CriarAlocacaoPatioEventoRequest request) {
        String chave = validarChave(chaveIdempotencia);
        validarRequest(request);
        String requestHash = hash(eventoId, request);

        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            return AlocacaoPatioEventoResponse.from(alocacoes.findByIdAndOrganizacaoId(
                            anterior.get().recursoId(), organizacaoId)
                    .orElseThrow(() -> new ConflitoException(
                            "O resultado idempotente nao esta mais disponivel.")));
        }

        Evento evento = eventoConfiguravel(eventoId, organizacaoId);
        validarIntersecaoComEvento(evento, request);
        PatioInstalacao patio = patioAtivoBloqueado(request.parkingFacilityId(), organizacaoId);
        if (alocacoes.existsByEventoIdAndPatioId(eventoId, patio.getId())) {
            throw new ConflitoException("O patio ja esta alocado a este evento.");
        }
        validarSaldoSobreposto(
                patio, request.startsAt(), request.endsAt(), request.operationalCapacity(), null);

        AlocacaoPatioEvento alocacao = AlocacaoPatioEvento.criar(
                organizacaoId, eventoId, patio.getId(), request.startsAt(), request.endsAt(),
                request.operationalCapacity(), request.sellableCapacity(), request.reservedCapacity());
        alocacao = alocacoes.saveAndFlush(alocacao);
        Map<String, Object> estado = snapshot(alocacao);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "CAPACIDADE_ALOCADA_AO_EVENTO", RECURSO, alocacao.getId(),
                eventoId, patio.getId(), null, null, estado, null, alocacao.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "CapacidadeAlocadaAoEvento", organizacaoId, RECURSO, alocacao.getId(),
                alocacao.getVersion(), null, 1, estado));
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave,
                requestHash, RECURSO, alocacao.getId());
        return AlocacaoPatioEventoResponse.from(alocacao);
    }

    @Transactional
    public AlocacaoPatioEventoResponse alterarCapacidades(
            Long organizacaoId, Long alocacaoId, long versaoEsperada,
            AlterarCapacidadesAlocacaoRequest request) {
        AlocacaoPatioEvento alocacao = alocacoes.findByIdAndOrganizacaoId(
                        alocacaoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao", alocacaoId));
        if (!alocacao.getVersion().equals(versaoEsperada)) {
            throw new VersaoAlocacaoDivergenteException();
        }
        PatioInstalacao patio = patioAtivoBloqueado(alocacao.getPatioId(), organizacaoId);
        validarSaldoSobreposto(
                patio, alocacao.getInicio(), alocacao.getFim(),
                request.operationalCapacity(), alocacao.getId());
        Map<String, Object> anterior = snapshot(alocacao);
        Long versaoAnterior = alocacao.getVersion();
        alocacao.alterarCapacidades(
                request.operationalCapacity(), request.sellableCapacity(), request.reservedCapacity());
        alocacoes.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "CAPACIDADE_DA_ALOCACAO_ALTERADA", RECURSO, alocacao.getId(),
                alocacao.getEventoId(), alocacao.getPatioId(), null, anterior, snapshot(alocacao),
                versaoAnterior, alocacao.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        return AlocacaoPatioEventoResponse.from(alocacao);
    }

    private Evento eventoConfiguravel(Long eventoId, Long organizacaoId) {
        Evento evento = eventos.findByIdAndOrganizacaoId(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        if (evento.getStatus() != StatusEvento.RASCUNHO
                && evento.getStatus() != StatusEvento.CONFIGURADO) {
            throw new ConflitoException("O estado do evento nao permite alocar patios.");
        }
        return evento;
    }

    private PatioInstalacao patioAtivoBloqueado(Long patioId, Long organizacaoId) {
        return patios.findAtivoByIdAndOrganizacaoIdForUpdate(patioId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Patio ativo", patioId));
    }

    private void validarSaldoSobreposto(
            PatioInstalacao patio, java.time.OffsetDateTime inicio, java.time.OffsetDateTime fim,
            int capacidadeSolicitada, Long excluirId) {
        long comprometida = alocacoes.somarCapacidadeOperacionalSobreposta(
                patio.getId(), inicio, fim, excluirId);
        if (comprometida + capacidadeSolicitada > patio.getCapacidadeOperacional()) {
            throw new ConflitoException(
                    "A capacidade operacional do patio seria sobrealocada neste periodo.");
        }
    }

    private static void validarIntersecaoComEvento(
            Evento evento, CriarAlocacaoPatioEventoRequest request) {
        boolean intersecta = request.startsAt().toInstant().isBefore(evento.getFim().toInstant())
                && request.endsAt().toInstant().isAfter(evento.getInicio().toInstant());
        if (!intersecta) {
            throw new IllegalArgumentException(
                    "O periodo da alocacao deve intersectar o periodo do evento.");
        }
    }

    private static void validarRequest(CriarAlocacaoPatioEventoRequest request) {
        if (request == null || request.parkingFacilityId() == null) {
            throw new IllegalArgumentException("A alocacao e o patio sao obrigatorios.");
        }
        AlocacaoPatioEvento.validarDados(
                request.startsAt(), request.endsAt(),
                request.operationalCapacity(), request.sellableCapacity(), request.reservedCapacity());
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O cabecalho Idempotency-Key e obrigatorio.");
        }
        String chave = valor.trim();
        if (chave.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return chave;
    }

    private static String hash(Long eventoId, CriarAlocacaoPatioEventoRequest request) {
        String canonico = campo(eventoId) + campo(request.parkingFacilityId())
                + campo(request.startsAt().toInstant()) + campo(request.endsAt().toInstant())
                + campo(request.operationalCapacity()) + campo(request.sellableCapacity())
                + campo(request.reservedCapacity());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static String campo(Object valor) {
        String texto = String.valueOf(valor);
        return texto.length() + ":" + texto;
    }

    private static Map<String, Object> snapshot(AlocacaoPatioEvento alocacao) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("eventoId", alocacao.getEventoId());
        estado.put("patioId", alocacao.getPatioId());
        estado.put("inicio", alocacao.getInicio().toInstant().toString());
        estado.put("fim", alocacao.getFim().toInstant().toString());
        estado.put("capacidadeOperacional", alocacao.getCapacidadeOperacional());
        estado.put("capacidadeVendavel", alocacao.getCapacidadeVendavel());
        estado.put("capacidadeReservada", alocacao.getCapacidadeReservada());
        return estado;
    }
}
