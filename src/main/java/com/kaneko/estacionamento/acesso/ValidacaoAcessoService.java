package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.ValidacaoAcessoResponse;
import com.kaneko.estacionamento.acesso.dto.ValidarAcessoRequest;
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
public class ValidacaoAcessoService {
    private static final String OPERACAO = "VALIDACAO_ACESSO_ONLINE";
    private static final String RECURSO = "TENTATIVA_ACESSO";

    private final TentativaAcessoRepository tentativas;
    private final EventoRepository eventos;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final RepresentacaoQrRepository representacoes;
    private final CredencialAcessoRepository credenciais;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public ValidacaoAcessoService(
            TentativaAcessoRepository tentativas, EventoRepository eventos,
            AlocacaoPatioEventoRepository alocacoes,
            RepresentacaoQrRepository representacoes,
            CredencialAcessoRepository credenciais,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.tentativas = tentativas;
        this.eventos = eventos;
        this.alocacoes = alocacoes;
        this.representacoes = representacoes;
        this.credenciais = credenciais;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional
    public ValidacaoAcessoResponse validar(
            Long organizacaoId, Long atorId, String chaveIdempotencia,
            ValidarAcessoRequest request) {
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
            return ValidacaoAcessoResponse.from(tentativas
                    .findByIdAndOrganizacaoIdAndAtorId(
                            anterior.get().recursoId(), organizacaoId, atorId)
                    .orElseThrow(() -> new ConflitoException(
                            "O resultado idempotente nao esta mais disponivel.")));
        }

        Evento evento = eventos.findByIdAndOrganizacaoId(
                        request.eventId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evento", request.eventId()));
        AlocacaoPatioEvento alocacao = alocacoes
                .findByOrganizacaoIdAndEventoIdAndPatioId(
                        organizacaoId, request.eventId(),
                        request.parkingFacilityId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Alocacao de patio do evento",
                        request.parkingFacilityId()));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        TentativaAcesso tentativa = TentativaAcesso.receberValidacao(
                organizacaoId, request.eventId(), request.parkingFacilityId(),
                atorId, request.lane(), agora);
        tentativa.avaliar();
        tentativa = tentativas.saveAndFlush(tentativa);

        if (evento.getStatus() != StatusEvento.EM_OPERACAO) {
            return recusar(tentativa,
                    MotivoDecisaoAcesso.EVENTO_FORA_DE_OPERACAO,
                    chave, requestHash, atorId, agora);
        }
        if (!dentroDaJanela(agora, evento.getInicio(), evento.getFim())
                || !dentroDaJanela(
                        agora, alocacao.getInicio(), alocacao.getFim())) {
            return recusar(tentativa, MotivoDecisaoAcesso.FORA_DA_JANELA,
                    chave, requestHash, atorId, agora);
        }

        RepresentacaoQr representacao = representacoes
                .findByOrganizacaoIdAndTokenHash(
                        organizacaoId, hashToken(request.qrToken()))
                .orElse(null);
        if (representacao == null || representacao.getRevogadaEm() != null
                || !agora.toInstant().isBefore(
                        representacao.getExpiraEm().toInstant())) {
            return recusar(tentativa, MotivoDecisaoAcesso.CREDENCIAL_INVALIDA,
                    chave, requestHash, atorId, agora);
        }
        CredencialAcesso credencial = credenciais
                .findByIdAndOrganizacaoId(
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
                agora, credencial.getInicioValidade(),
                credencial.getFimValidade())) {
            return recusar(tentativa, MotivoDecisaoAcesso.FORA_DA_JANELA,
                    chave, requestHash, atorId, agora);
        }
        if (alocacao.getOcupacaoAtual()
                >= alocacao.getCapacidadeOperacional()) {
            return recusar(tentativa, MotivoDecisaoAcesso.PATIO_LOTADO,
                    chave, requestHash, atorId, agora);
        }

        tentativa.autorizarSemConsumo(
                credencial.getId(), representacao.getId(), agora);
        tentativas.flush();
        registrar(tentativa, true);
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO, chave, requestHash,
                RECURSO, tentativa.getId());
        return ValidacaoAcessoResponse.from(tentativa);
    }

    private ValidacaoAcessoResponse recusar(
            TentativaAcesso tentativa, MotivoDecisaoAcesso motivo,
            String chave, String requestHash, Long atorId,
            OffsetDateTime instante) {
        tentativa.recusar(motivo, instante);
        tentativas.flush();
        registrar(tentativa, false);
        idempotencia.registrar(
                tentativa.getOrganizacaoId(), atorId, OPERACAO, chave,
                requestHash, RECURSO, tentativa.getId());
        return ValidacaoAcessoResponse.from(tentativa);
    }

    private void registrar(TentativaAcesso tentativa, boolean autorizada) {
        Map<String, Object> estado = snapshot(tentativa);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                tentativa.getOrganizacaoId(), autorizada
                        ? "VALIDACAO_ACESSO_AUTORIZADA"
                        : "VALIDACAO_ACESSO_RECUSADA",
                RECURSO, tentativa.getId(), tentativa.getEventoId(),
                tentativa.getPatioId(), autorizada
                        ? null : tentativa.getMotivo().name(),
                null, estado, null, tentativa.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                autorizada ? "AcessoAutorizado" : "AcessoRecusado",
                tentativa.getOrganizacaoId(), RECURSO, tentativa.getId(),
                tentativa.getVersion(), null, 1, estado));
    }

    private static Map<String, Object> snapshot(TentativaAcesso tentativa) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("tentativaAcessoId", tentativa.getId());
        estado.put("eventoId", tentativa.getEventoId());
        estado.put("patioId", tentativa.getPatioId());
        estado.put("operacao", tentativa.getOperacao().name());
        estado.put("direcao", tentativa.getDirecao());
        estado.put("origemLeitura", tentativa.getOrigemLeitura());
        estado.put("faixa", tentativa.getFaixa());
        estado.put("decisao", tentativa.getStatus().name());
        estado.put("motivo", tentativa.getMotivo().name());
        estado.put("consumoRealizado", false);
        if (tentativa.getCredencialId() != null) {
            estado.put("credencialId", tentativa.getCredencialId());
        }
        estado.put("decididoEm",
                tentativa.getDecididoEm().toInstant().toString());
        return estado;
    }

    private static boolean dentroDaJanela(
            OffsetDateTime instante, OffsetDateTime inicio,
            OffsetDateTime fim) {
        return !instante.toInstant().isBefore(inicio.toInstant())
                && instante.toInstant().isBefore(fim.toInstant());
    }

    private static void validarRequest(ValidarAcessoRequest request) {
        if (request == null || request.qrToken() == null
                || !request.qrToken().matches("KQ1-[A-Za-z0-9_-]{43}")
                || request.eventId() == null
                || request.parkingFacilityId() == null
                || request.lane() == null || request.lane().isBlank()
                || request.lane().trim().length() > 80) {
            throw new IllegalArgumentException(
                    "Os dados da validacao de acesso sao invalidos.");
        }
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank() || valor.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter entre 1 e 100 caracteres.");
        }
        return valor.trim();
    }

    private static String hashRequest(ValidarAcessoRequest request) {
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
}
