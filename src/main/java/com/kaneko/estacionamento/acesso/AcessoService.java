package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.CheckinResponse;
import com.kaneko.estacionamento.acesso.dto.RealizarCheckinRequest;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.credencial.CredencialAcesso;
import com.kaneko.estacionamento.credencial.CredencialAcessoRepository;
import com.kaneko.estacionamento.credencial.RepresentacaoQr;
import com.kaneko.estacionamento.credencial.RepresentacaoQrRepository;
import com.kaneko.estacionamento.credencial.StatusCredencial;
import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEvento;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcessoService {
    private static final String OPERACAO = "CHECKIN_ONLINE";
    private static final String RECURSO = "TENTATIVA_ACESSO";

    private final TentativaAcessoRepository tentativas;
    private final PresencaRepository presencas;
    private final SessaoEstacionamentoRepository sessoes;
    private final EventoRepository eventos;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final RepresentacaoQrRepository representacoes;
    private final CredencialAcessoRepository credenciais;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public AcessoService(
            TentativaAcessoRepository tentativas, PresencaRepository presencas,
            SessaoEstacionamentoRepository sessoes, EventoRepository eventos,
            AlocacaoPatioEventoRepository alocacoes,
            RepresentacaoQrRepository representacoes,
            CredencialAcessoRepository credenciais,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.tentativas = tentativas;
        this.presencas = presencas;
        this.sessoes = sessoes;
        this.eventos = eventos;
        this.alocacoes = alocacoes;
        this.representacoes = representacoes;
        this.credenciais = credenciais;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional
    public CheckinResponse realizarCheckin(
            Long organizacaoId, Long atorId, String chaveIdempotencia,
            RealizarCheckinRequest request) {
        String chave = validarChave(chaveIdempotencia);
        validarRequest(request);
        String requestHash = hashRequest(request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO, chave);
        var anterior = idempotencia.buscar(
                organizacaoId, atorId, OPERACAO, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            TentativaAcesso tentativa = tentativas
                    .findByIdAndOrganizacaoIdAndAtorId(
                            anterior.get().recursoId(), organizacaoId, atorId)
                    .orElseThrow(() -> new ConflitoException(
                            "O resultado idempotente nao esta mais disponivel."));
            return resposta(tentativa);
        }

        Evento evento = eventos.findByIdAndOrganizacaoId(
                        request.eventId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evento", request.eventId()));
        if (!alocacoes.existsByOrganizacaoIdAndEventoIdAndPatioId(
                organizacaoId, request.eventId(), request.parkingFacilityId())) {
            throw new RecursoNaoEncontradoException(
                    "Alocacao de patio do evento", request.parkingFacilityId());
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        TentativaAcesso tentativa = TentativaAcesso.receber(
                organizacaoId, request.eventId(), request.parkingFacilityId(),
                atorId, request.lane(), agora);
        tentativa.avaliar();
        tentativa = tentativas.saveAndFlush(tentativa);

        if (evento.getStatus() != StatusEvento.EM_OPERACAO) {
            return recusar(tentativa, MotivoDecisaoAcesso.EVENTO_FORA_DE_OPERACAO,
                    chave, requestHash, atorId, agora);
        }
        if (!dentroDaJanela(agora, evento.getInicio(), evento.getFim())) {
            return recusar(tentativa, MotivoDecisaoAcesso.FORA_DA_JANELA,
                    chave, requestHash, atorId, agora);
        }

        RepresentacaoQr representacao = representacoes.findByTokenHashForUpdate(
                        organizacaoId, hashToken(request.qrToken()))
                .orElse(null);
        if (representacao == null || representacao.getRevogadaEm() != null
                || !agora.toInstant().isBefore(
                        representacao.getExpiraEm().toInstant())) {
            return recusar(tentativa, MotivoDecisaoAcesso.CREDENCIAL_INVALIDA,
                    chave, requestHash, atorId, agora);
        }
        CredencialAcesso credencial = credenciais
                .findByIdAndOrganizacaoIdForUpdate(
                        representacao.getCredencialId(), organizacaoId)
                .orElse(null);
        if (credencial == null) {
            return recusar(tentativa, MotivoDecisaoAcesso.CREDENCIAL_INVALIDA,
                    chave, requestHash, atorId, agora);
        }
        tentativa.vincularCredencial(credencial.getId(), representacao.getId());
        tentativas.flush();
        if (!credencial.getEventoId().equals(request.eventId())) {
            return recusar(tentativa, MotivoDecisaoAcesso.CREDENCIAL_INVALIDA,
                    chave, requestHash, atorId, agora);
        }
        if (!credencial.getPatioId().equals(request.parkingFacilityId())) {
            return recusar(tentativa, MotivoDecisaoAcesso.PATIO_INCORRETO,
                    chave, requestHash, atorId, agora);
        }
        if (credencial.getStatus() == StatusCredencial.DENTRO) {
            return recusar(tentativa, MotivoDecisaoAcesso.ENTRADA_DUPLICADA,
                    chave, requestHash, atorId, agora);
        }
        if (credencial.getStatus() != StatusCredencial.ATIVA) {
            return recusar(tentativa, MotivoDecisaoAcesso.CREDENCIAL_INATIVA,
                    chave, requestHash, atorId, agora);
        }
        if (!dentroDaJanela(
                agora, credencial.getInicioValidade(), credencial.getFimValidade())) {
            return recusar(tentativa, MotivoDecisaoAcesso.FORA_DA_JANELA,
                    chave, requestHash, atorId, agora);
        }

        AlocacaoPatioEvento alocacao = alocacoes.findByEventoAndPatioForUpdate(
                        organizacaoId, request.eventId(),
                        request.parkingFacilityId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Alocacao de patio do evento", request.parkingFacilityId()));
        if (eventos.findStatusByIdAndOrganizacaoId(
                        request.eventId(), organizacaoId)
                .filter(StatusEvento.EM_OPERACAO::equals).isEmpty()) {
            return recusar(tentativa, MotivoDecisaoAcesso.EVENTO_FORA_DE_OPERACAO,
                    chave, requestHash, atorId, agora);
        }
        if (!dentroDaJanela(agora, alocacao.getInicio(), alocacao.getFim())) {
            return recusar(tentativa, MotivoDecisaoAcesso.FORA_DA_JANELA,
                    chave, requestHash, atorId, agora);
        }
        if (alocacao.getOcupacaoAtual() >= alocacao.getCapacidadeOperacional()) {
            return recusar(tentativa, MotivoDecisaoAcesso.PATIO_LOTADO,
                    chave, requestHash, atorId, agora);
        }

        credencial.registrarEntrada();
        int ocupacao = alocacao.registrarEntrada();
        Presenca presenca = presencas.saveAndFlush(Presenca.entrar(
                organizacaoId, request.eventId(), request.parkingFacilityId(),
                credencial.getId(), tentativa.getId(), agora));
        SessaoEstacionamento sessao = sessoes.saveAndFlush(
                SessaoEstacionamento.abrir(
                        organizacaoId, request.eventId(),
                        request.parkingFacilityId(), credencial.getId(),
                        credencial.getPedidoId(), credencial.getItemPedidoId(),
                        presenca.getId(), tentativa.getId(),
                        credencial.getPlacaVeiculo(), agora));
        tentativa.autorizar(
                credencial.getId(), representacao.getId(), ocupacao, agora);
        credenciais.flush();
        alocacoes.flush();
        tentativas.flush();
        registrarAutorizacao(tentativa, presenca, sessao, alocacao);
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO, chave, requestHash,
                RECURSO, tentativa.getId());
        return CheckinResponse.from(tentativa, presenca, sessao);
    }

    private CheckinResponse recusar(
            TentativaAcesso tentativa, MotivoDecisaoAcesso motivo,
            String chave, String requestHash, Long atorId,
            OffsetDateTime instante) {
        tentativa.recusar(motivo, instante);
        tentativas.flush();
        Map<String, Object> estado = snapshotTentativa(tentativa);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                tentativa.getOrganizacaoId(), "CHECKIN_RECUSADO", RECURSO,
                tentativa.getId(), tentativa.getEventoId(), tentativa.getPatioId(),
                motivo.name(), null, estado, null, tentativa.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "AcessoRecusado", tentativa.getOrganizacaoId(), RECURSO,
                tentativa.getId(), tentativa.getVersion(), null, 1, estado));
        idempotencia.registrar(
                tentativa.getOrganizacaoId(), atorId, OPERACAO, chave,
                requestHash, RECURSO, tentativa.getId());
        return CheckinResponse.from(tentativa, null, null);
    }

    private void registrarAutorizacao(
            TentativaAcesso tentativa, Presenca presenca,
            SessaoEstacionamento sessao, AlocacaoPatioEvento alocacao) {
        Map<String, Object> acesso = snapshotTentativa(tentativa);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                tentativa.getOrganizacaoId(), "CHECKIN_AUTORIZADO", RECURSO,
                tentativa.getId(), tentativa.getEventoId(), tentativa.getPatioId(),
                null, null, acesso, null, tentativa.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "AcessoAutorizado", tentativa.getOrganizacaoId(), RECURSO,
                tentativa.getId(), tentativa.getVersion(), null, 1, acesso));

        Map<String, Object> checkin = new LinkedHashMap<>();
        checkin.put("presencaId", presenca.getId());
        checkin.put("tentativaAcessoId", tentativa.getId());
        checkin.put("credencialId", tentativa.getCredencialId());
        checkin.put("eventoId", tentativa.getEventoId());
        checkin.put("patioId", tentativa.getPatioId());
        checkin.put("checkinEm", presenca.getCheckinEm().toInstant().toString());
        outbox.publicar(new PublicarEventoOutboxCommand(
                "CheckinRealizado", tentativa.getOrganizacaoId(), "PRESENCA",
                presenca.getId(), presenca.getVersion(), null, 1, checkin));

        Map<String, Object> movimento = new LinkedHashMap<>();
        movimento.put("sessaoId", sessao.getId());
        movimento.put("presencaId", presenca.getId());
        movimento.put("credencialId", tentativa.getCredencialId());
        movimento.put("eventoId", tentativa.getEventoId());
        movimento.put("patioId", tentativa.getPatioId());
        movimento.put("origemDireito", sessao.getOrigemDireito());
        movimento.put("entradaEm", sessao.getEntradaEm().toInstant().toString());
        outbox.publicar(new PublicarEventoOutboxCommand(
                "MovimentacaoAberta", tentativa.getOrganizacaoId(),
                "SESSAO_ESTACIONAMENTO", sessao.getId(), sessao.getVersion(),
                null, 1, movimento));

        Map<String, Object> ocupacao = new LinkedHashMap<>();
        ocupacao.put("eventoId", tentativa.getEventoId());
        ocupacao.put("patioId", tentativa.getPatioId());
        ocupacao.put("ocupacaoAtual", alocacao.getOcupacaoAtual());
        ocupacao.put("capacidadeOperacional", alocacao.getCapacidadeOperacional());
        outbox.publicar(new PublicarEventoOutboxCommand(
                "OcupacaoAlterada", tentativa.getOrganizacaoId(),
                "ALOCACAO_PATIO_EVENTO", alocacao.getId(),
                alocacao.getVersion(), null, 1, ocupacao));
    }

    private CheckinResponse resposta(TentativaAcesso tentativa) {
        Presenca presenca = presencas.findByTentativaCheckinId(
                tentativa.getId()).orElse(null);
        SessaoEstacionamento sessao = sessoes.findByTentativaEntradaId(
                tentativa.getId()).orElse(null);
        return CheckinResponse.from(tentativa, presenca, sessao);
    }

    private static boolean dentroDaJanela(
            OffsetDateTime instante, OffsetDateTime inicio,
            OffsetDateTime fim) {
        return !instante.toInstant().isBefore(inicio.toInstant())
                && instante.toInstant().isBefore(fim.toInstant());
    }

    private static void validarRequest(RealizarCheckinRequest request) {
        if (request == null || request.qrToken() == null
                || !request.qrToken().matches("KQ1-[A-Za-z0-9_-]{43}")
                || request.eventId() == null || request.parkingFacilityId() == null
                || request.lane() == null || request.lane().isBlank()
                || request.lane().trim().length() > 80) {
            throw new IllegalArgumentException(
                    "Os dados do check-in online sao invalidos.");
        }
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank() || valor.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter entre 1 e 100 caracteres.");
        }
        return valor.trim();
    }

    private static String hashRequest(RealizarCheckinRequest request) {
        return sha256(campo(request.qrToken()) + campo(request.eventId())
                + campo(request.parkingFacilityId())
                + campo(request.lane().trim()));
    }

    private static String hashToken(String token) {
        return sha256(token);
    }

    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static String campo(Object valor) {
        String texto = String.valueOf(valor);
        return texto.length() + ":" + texto;
    }

    private static Map<String, Object> snapshotTentativa(
            TentativaAcesso tentativa) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("tentativaAcessoId", tentativa.getId());
        estado.put("eventoId", tentativa.getEventoId());
        estado.put("patioId", tentativa.getPatioId());
        estado.put("direcao", tentativa.getDirecao());
        estado.put("origemLeitura", tentativa.getOrigemLeitura());
        estado.put("faixa", tentativa.getFaixa());
        estado.put("decisao", tentativa.getStatus().name());
        estado.put("motivo", tentativa.getMotivo().name());
        if (tentativa.getCredencialId() != null) {
            estado.put("credencialId", tentativa.getCredencialId());
        }
        if (tentativa.getOcupacaoResultante() != null) {
            estado.put("ocupacaoResultante", tentativa.getOcupacaoResultante());
        }
        estado.put("decididoEm", tentativa.getDecididoEm().toInstant().toString());
        return estado;
    }
}
