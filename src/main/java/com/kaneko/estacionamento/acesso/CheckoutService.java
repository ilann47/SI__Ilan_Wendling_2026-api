package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.CheckoutResponse;
import com.kaneko.estacionamento.acesso.dto.RealizarCheckoutRequest;
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
import com.kaneko.estacionamento.evento.PoliticaReentrada;
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
public class CheckoutService {
    private static final String OPERACAO = "CHECKOUT_ONLINE";
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

    public CheckoutService(
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
    public CheckoutResponse realizarCheckout(
            Long organizacaoId, Long atorId, String chaveIdempotencia,
            RealizarCheckoutRequest request) {
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
        TentativaAcesso tentativa = TentativaAcesso.receberSaida(
                organizacaoId, request.eventId(), request.parkingFacilityId(),
                atorId, request.lane(), agora);
        tentativa.avaliar();
        tentativa = tentativas.saveAndFlush(tentativa);

        if (evento.getStatus() != StatusEvento.EM_OPERACAO) {
            return recusar(tentativa, MotivoDecisaoAcesso.EVENTO_FORA_DE_OPERACAO,
                    chave, requestHash, atorId, agora);
        }
        RepresentacaoQr representacao = representacoes.findByTokenHashForUpdate(
                        organizacaoId, sha256(request.qrToken()))
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
        if (credencial.getStatus() != StatusCredencial.DENTRO) {
            return recusar(tentativa, MotivoDecisaoAcesso.SAIDA_DUPLICADA,
                    chave, requestHash, atorId, agora);
        }

        Presenca presenca = presencas.findAbertaForUpdate(
                        organizacaoId, credencial.getId()).orElse(null);
        SessaoEstacionamento sessao = sessoes.findAbertaForUpdate(
                        organizacaoId, credencial.getId()).orElse(null);
        if (presenca == null || sessao == null) {
            return recusar(tentativa, MotivoDecisaoAcesso.PRESENCA_INEXISTENTE,
                    chave, requestHash, atorId, agora);
        }
        AlocacaoPatioEvento alocacao = alocacoes.findByEventoAndPatioForUpdate(
                        organizacaoId, request.eventId(), request.parkingFacilityId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Alocacao de patio do evento", request.parkingFacilityId()));
        if (eventos.findStatusByIdAndOrganizacaoId(
                        request.eventId(), organizacaoId)
                .filter(StatusEvento.EM_OPERACAO::equals).isEmpty()) {
            return recusar(tentativa, MotivoDecisaoAcesso.EVENTO_FORA_DE_OPERACAO,
                    chave, requestHash, atorId, agora);
        }

        int ocupacao = alocacao.registrarSaida();
        tentativa.autorizar(
                credencial.getId(), representacao.getId(), ocupacao, agora);
        tentativas.flush();
        presenca.sair(tentativa.getId(), agora);
        sessao.encerrar(tentativa.getId(), agora);
        boolean permiteReentrada = evento.getPoliticaReentrada()
                == PoliticaReentrada.REENTRADA_PERMITIDA;
        credencial.registrarSaida(permiteReentrada);
        presencas.flush();
        sessoes.flush();
        credenciais.flush();
        alocacoes.flush();
        registrarAutorizacao(
                tentativa, presenca, sessao, alocacao, credencial);
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO, chave, requestHash,
                RECURSO, tentativa.getId());
        return CheckoutResponse.from(
                tentativa, presenca, sessao, credencial);
    }

    private CheckoutResponse recusar(
            TentativaAcesso tentativa, MotivoDecisaoAcesso motivo,
            String chave, String requestHash, Long atorId,
            OffsetDateTime instante) {
        tentativa.recusar(motivo, instante);
        tentativas.flush();
        Map<String, Object> estado = snapshotTentativa(tentativa);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                tentativa.getOrganizacaoId(), "CHECKOUT_RECUSADO", RECURSO,
                tentativa.getId(), tentativa.getEventoId(), tentativa.getPatioId(),
                motivo.name(), null, estado, null, tentativa.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "AcessoRecusado", tentativa.getOrganizacaoId(), RECURSO,
                tentativa.getId(), tentativa.getVersion(), null, 1, estado));
        idempotencia.registrar(
                tentativa.getOrganizacaoId(), atorId, OPERACAO, chave,
                requestHash, RECURSO, tentativa.getId());
        return CheckoutResponse.from(tentativa, null, null, null);
    }

    private void registrarAutorizacao(
            TentativaAcesso tentativa, Presenca presenca,
            SessaoEstacionamento sessao, AlocacaoPatioEvento alocacao,
            CredencialAcesso credencial) {
        Map<String, Object> acesso = snapshotTentativa(tentativa);
        acesso.put("statusCredencial", credencial.getStatus().name());
        auditoria.registrar(new RegistrarAuditoriaCommand(
                tentativa.getOrganizacaoId(), "CHECKOUT_AUTORIZADO", RECURSO,
                tentativa.getId(), tentativa.getEventoId(), tentativa.getPatioId(),
                null, null, acesso, null, tentativa.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "AcessoAutorizado", tentativa.getOrganizacaoId(), RECURSO,
                tentativa.getId(), tentativa.getVersion(), null, 1, acesso));

        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("presencaId", presenca.getId());
        checkout.put("tentativaAcessoId", tentativa.getId());
        checkout.put("credencialId", credencial.getId());
        checkout.put("eventoId", tentativa.getEventoId());
        checkout.put("patioId", tentativa.getPatioId());
        checkout.put("checkoutEm", presenca.getCheckoutEm().toInstant().toString());
        checkout.put("statusCredencial", credencial.getStatus().name());
        outbox.publicar(new PublicarEventoOutboxCommand(
                "CheckoutRealizado", tentativa.getOrganizacaoId(), "PRESENCA",
                presenca.getId(), presenca.getVersion(), null, 1, checkout));

        Map<String, Object> movimento = new LinkedHashMap<>();
        movimento.put("sessaoId", sessao.getId());
        movimento.put("presencaId", presenca.getId());
        movimento.put("credencialId", credencial.getId());
        movimento.put("eventoId", tentativa.getEventoId());
        movimento.put("patioId", tentativa.getPatioId());
        movimento.put("saidaEm", sessao.getSaidaEm().toInstant().toString());
        outbox.publicar(new PublicarEventoOutboxCommand(
                "MovimentacaoEncerrada", tentativa.getOrganizacaoId(),
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

    private CheckoutResponse resposta(TentativaAcesso tentativa) {
        Presenca presenca = presencas.findByTentativaCheckoutId(
                tentativa.getId()).orElse(null);
        SessaoEstacionamento sessao = sessoes.findByTentativaSaidaId(
                tentativa.getId()).orElse(null);
        String statusResultante = null;
        if (tentativa.getStatus() == StatusTentativaAcesso.AUTORIZADA) {
            Evento evento = eventos.findByIdAndOrganizacaoId(
                            tentativa.getEventoId(), tentativa.getOrganizacaoId())
                    .orElseThrow(() -> new ConflitoException(
                            "A politica do resultado idempotente nao esta disponivel."));
            statusResultante = evento.getPoliticaReentrada()
                    == PoliticaReentrada.REENTRADA_PERMITIDA
                    ? StatusCredencial.ATIVA.name()
                    : StatusCredencial.CONSUMIDA.name();
        }
        return CheckoutResponse.fromStatus(
                tentativa, presenca, sessao, statusResultante);
    }

    private static void validarRequest(RealizarCheckoutRequest request) {
        if (request == null || request.qrToken() == null
                || !request.qrToken().matches("KQ1-[A-Za-z0-9_-]{43}")
                || request.eventId() == null || request.parkingFacilityId() == null
                || request.lane() == null || request.lane().isBlank()
                || request.lane().trim().length() > 80) {
            throw new IllegalArgumentException(
                    "Os dados do check-out online sao invalidos.");
        }
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank() || valor.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter entre 1 e 100 caracteres.");
        }
        return valor.trim();
    }

    private static String hashRequest(RealizarCheckoutRequest request) {
        return sha256(campo(request.qrToken()) + campo(request.eventId())
                + campo(request.parkingFacilityId())
                + campo(request.lane().trim()));
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
        estado.put("operacao", tentativa.getOperacao().name());
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
