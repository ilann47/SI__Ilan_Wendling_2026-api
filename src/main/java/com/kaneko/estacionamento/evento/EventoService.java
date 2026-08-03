package com.kaneko.estacionamento.evento;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.acesso.PresencaRepository;
import com.kaneko.estacionamento.acesso.SessaoEstacionamentoRepository;
import com.kaneko.estacionamento.acesso.StatusPresenca;
import com.kaneko.estacionamento.acesso.StatusSessaoEstacionamento;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.evento.dto.AbrirVendasEventoRequest;
import com.kaneko.estacionamento.evento.dto.AtualizarEventoRequest;
import com.kaneko.estacionamento.evento.dto.ChecklistConfiguracaoEventoResponse;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.evento.dto.EventoResponse;
import com.kaneko.estacionamento.evento.dto.EncerrarOperacaoEventoRequest;
import com.kaneko.estacionamento.evento.dto.PublicarEventoRequest;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamentoRepository;
import com.kaneko.estacionamento.inventario.produto.StatusProdutoEstacionamento;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import com.kaneko.estacionamento.organizacao.venue.VenueRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventoService {
    private static final String OPERACAO_CRIAR = "EVENTO_CRIAR";
    private static final String RECURSO = "EVENTO";

    private final EventoRepository eventos;
    private final OrganizacaoRepository organizacoes;
    private final VenueRepository venues;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final ProdutoEstacionamentoRepository produtos;
    private final PresencaRepository presencas;
    private final SessaoEstacionamentoRepository sessoes;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public EventoService(
            EventoRepository eventos, OrganizacaoRepository organizacoes,
            VenueRepository venues, AlocacaoPatioEventoRepository alocacoes,
            ProdutoEstacionamentoRepository produtos,
            PresencaRepository presencas,
            SessaoEstacionamentoRepository sessoes,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.eventos = eventos;
        this.organizacoes = organizacoes;
        this.venues = venues;
        this.alocacoes = alocacoes;
        this.produtos = produtos;
        this.presencas = presencas;
        this.sessoes = sessoes;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional
    public EventoResponse atualizar(
            Long organizacaoId, Long eventoId, long versaoEsperada,
            AtualizarEventoRequest request) {
        if (request == null || request.reentryPolicy() == null) {
            throw new IllegalArgumentException(
                    "A politica de reentrada e obrigatoria.");
        }
        Evento evento = buscarParaTransicao(
                organizacaoId, eventoId, versaoEsperada);
        if (evento.getPoliticaReentrada() == request.reentryPolicy()) {
            return EventoResponse.from(evento);
        }
        Map<String, Object> anterior = snapshot(evento);
        Long versaoAnterior = evento.getVersion();
        evento.definirPoliticaReentrada(request.reentryPolicy());
        eventos.flush();
        registrarTransicao(
                evento, "POLITICA_REENTRADA_DEFINIDA",
                "PoliticaReentradaDefinida", anterior, versaoAnterior);
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse criar(
            Long organizacaoId, Long atorId, String chaveIdempotencia,
            CriarEventoRequest request) {
        String chave = validarChave(chaveIdempotencia);
        DadosCriacao dados = normalizar(request);
        String requestHash = hash(dados);

        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            Evento existente = eventos.findByIdAndOrganizacaoId(
                            anterior.get().recursoId(), organizacaoId)
                    .orElseThrow(() -> new ConflitoException(
                            "O resultado idempotente nao esta mais disponivel."));
            return EventoResponse.from(existente);
        }

        organizacoes.findByIdAndStatus(organizacaoId, StatusOrganizacao.ATIVA)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Organizacao ativa", organizacaoId));
        venues.findByIdAndOrganizacaoIdAndAtivoTrue(dados.venueId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Venue ativo", dados.venueId()));
        if (dados.externalId() != null
                && eventos.existsByOrganizacaoIdAndExternalIdIgnoreCase(
                        organizacaoId, dados.externalId())) {
            throw new ConflitoException("O externalId ja identifica outro evento no tenant.");
        }

        Evento evento = Evento.criar(
                organizacaoId, dados.venueId(), dados.nome(), dados.inicio(), dados.fim(),
                dados.fusoHorario(), dados.externalId());
        evento = eventos.saveAndFlush(evento);

        Map<String, Object> estado = snapshot(evento);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "EVENTO_CRIADO", RECURSO, evento.getId(), evento.getId(),
                null, null, null, estado, null, evento.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "EventoCriado", organizacaoId, RECURSO, evento.getId(), evento.getVersion(),
                null, 1, estado));
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave,
                requestHash, RECURSO, evento.getId());
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse publicar(
            Long organizacaoId, Long eventoId, long versaoEsperada,
            PublicarEventoRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException("A confirmacao da publicacao e obrigatoria.");
        }
        Evento evento = eventos.findByIdAndOrganizacaoIdForUpdate(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        if (!evento.getVersion().equals(versaoEsperada)) {
            throw new VersaoEventoDivergenteException();
        }
        if (evento.getStatus() != StatusEvento.RASCUNHO
                && evento.getStatus() != StatusEvento.CONFIGURADO) {
            throw new ConflitoException(
                    "Somente evento em rascunho ou configurado pode ser publicado.");
        }
        boolean possuiAlocacao = alocacoes
                .existsByEventoIdAndOrganizacaoIdAndCapacidadeVendavelGreaterThan(
                        eventoId, organizacaoId, 0);
        if (!possuiAlocacao) {
            throw new ConflitoException(
                    "O evento precisa possuir alocacao com capacidade vendavel positiva.");
        }
        boolean possuiProdutoPublicado = produtos.existsByEventoIdAndOrganizacaoIdAndStatus(
                eventoId, organizacaoId, StatusProdutoEstacionamento.PUBLICADO);
        if (!possuiProdutoPublicado) {
            throw new ConflitoException(
                    "O evento precisa possuir ao menos um produto publicado.");
        }
        if (evento.getPoliticaReentrada() == null) {
            throw new ConflitoException(
                    "O evento precisa possuir politica de reentrada explicita.");
        }

        Map<String, Object> anterior = snapshot(evento);
        Long versaoAnterior = evento.getVersion();
        if (evento.getStatus() == StatusEvento.RASCUNHO) {
            evento.configurar();
            eventos.flush();
            Map<String, Object> configurado = snapshot(evento);
            outbox.publicar(new PublicarEventoOutboxCommand(
                    "EventoConfigurado", organizacaoId, RECURSO, evento.getId(),
                    evento.getVersion(), null, 1, configurado));
        }
        evento.publicar();
        eventos.flush();
        Map<String, Object> publicado = snapshot(evento);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "EVENTO_PUBLICADO", RECURSO, evento.getId(), evento.getId(),
                null, null, anterior, publicado, versaoAnterior, evento.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "EventoPublicado", organizacaoId, RECURSO, evento.getId(),
                evento.getVersion(), null, 1, publicado));
        return EventoResponse.from(
                evento, ChecklistConfiguracaoEventoResponse.primeiraEntregaPronta());
    }

    @Transactional
    public EventoResponse abrirVendas(
            Long organizacaoId, Long eventoId, long versaoEsperada,
            AbrirVendasEventoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("O comando de abertura de vendas e obrigatorio.");
        }
        OffsetDateTime agora = OffsetDateTime.now();
        if (request.startsAt() != null
                && request.startsAt().toInstant().isAfter(agora.toInstant())) {
            throw new ConflitoException(
                    "Abertura agendada depende do scheduler e ainda nao esta disponivel.");
        }
        Evento evento = eventos.findByIdAndOrganizacaoIdForUpdate(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        if (!evento.getVersion().equals(versaoEsperada)) {
            throw new VersaoEventoDivergenteException();
        }
        if (evento.getStatus() != StatusEvento.PUBLICADO) {
            throw new ConflitoException(
                    "Somente evento publicado pode abrir vendas.");
        }
        Map<String, Object> anterior = snapshot(evento);
        Long versaoAnterior = evento.getVersion();
        evento.abrirVendas();
        eventos.flush();
        Map<String, Object> posterior = snapshot(evento);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "VENDAS_EVENTO_ABERTAS", RECURSO,
                evento.getId(), evento.getId(), null, null, anterior, posterior,
                versaoAnterior, evento.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "VendasDoEventoAbertas", organizacaoId, RECURSO, evento.getId(),
                evento.getVersion(), null, 1, posterior));
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse encerrarVendas(
            Long organizacaoId, Long eventoId, long versaoEsperada,
            PublicarEventoRequest request) {
        validarConfirmacao(request, "encerramento das vendas");
        Evento evento = buscarParaTransicao(
                organizacaoId, eventoId, versaoEsperada);
        if (evento.getStatus() != StatusEvento.VENDAS_ABERTAS) {
            throw new ConflitoException(
                    "Somente evento com vendas abertas pode encerrar vendas.");
        }
        Map<String, Object> anterior = snapshot(evento);
        Long versaoAnterior = evento.getVersion();
        evento.encerrarVendas();
        eventos.flush();
        registrarTransicao(
                evento, "VENDAS_EVENTO_ENCERRADAS", "VendasDoEventoEncerradas",
                anterior, versaoAnterior);
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse iniciarOperacao(
            Long organizacaoId, Long eventoId, long versaoEsperada,
            PublicarEventoRequest request) {
        validarConfirmacao(request, "inicio da operacao");
        Evento evento = buscarParaTransicao(
                organizacaoId, eventoId, versaoEsperada);
        if (evento.getStatus() != StatusEvento.VENDAS_ENCERRADAS) {
            throw new ConflitoException(
                    "Somente evento com vendas encerradas pode iniciar operacao.");
        }
        Map<String, Object> anterior = snapshot(evento);
        Long versaoAnterior = evento.getVersion();
        evento.iniciarOperacao();
        eventos.flush();
        registrarTransicao(
                evento, "OPERACAO_EVENTO_INICIADA", "OperacaoDoEventoIniciada",
                anterior, versaoAnterior);
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse encerrarOperacao(
            Long organizacaoId, Long eventoId, long versaoEsperada,
            EncerrarOperacaoEventoRequest request) {
        validarEncerramentoOperacional(request);
        Evento evento = buscarParaTransicao(
                organizacaoId, eventoId, versaoEsperada);
        if (evento.getStatus() != StatusEvento.EM_OPERACAO) {
            throw new ConflitoException(
                    "Somente evento em operacao pode encerrar a operacao.");
        }

        var alocacoesDoEvento = alocacoes.findAllByEventoForUpdate(
                organizacaoId, eventoId);
        boolean possuiOcupacao = alocacoesDoEvento.stream()
                .anyMatch(alocacao -> alocacao.getOcupacaoAtual() > 0);
        boolean possuiPresenca = presencas.existsByOrganizacaoIdAndEventoIdAndStatus(
                organizacaoId, eventoId, StatusPresenca.DENTRO);
        boolean possuiSessao = sessoes.existsByOrganizacaoIdAndEventoIdAndStatus(
                organizacaoId, eventoId, StatusSessaoEstacionamento.ABERTA);
        if (possuiOcupacao || possuiPresenca || possuiSessao) {
            throw new ConflitoException(
                    "A operacao possui ocupacao, presenca ou sessao aberta.");
        }

        Map<String, Object> anterior = snapshot(evento);
        Long versaoAnterior = evento.getVersion();
        evento.encerrarOperacao();
        eventos.flush();
        Map<String, Object> posterior = snapshot(evento);
        posterior.put("ocupacaoAberta", 0);
        posterior.put("presencasAbertas", 0);
        posterior.put("sessoesAbertas", 0);
        posterior.put("fechamentoFinanceiro", "PENDENTE");
        posterior.put("ressalvaFinanceiraReconhecida", true);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "OPERACAO_EVENTO_ENCERRADA", RECURSO,
                evento.getId(), evento.getId(), null,
                "FONTES_FINANCEIRAS_PENDENTES", anterior, posterior,
                versaoAnterior, evento.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "OperacaoDoEventoEncerrada", organizacaoId, RECURSO,
                evento.getId(), evento.getVersion(), null, 1, posterior));
        return EventoResponse.from(evento);
    }

    private Evento buscarParaTransicao(
            Long organizacaoId, Long eventoId, long versaoEsperada) {
        Evento evento = eventos.findByIdAndOrganizacaoIdForUpdate(
                        eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evento", eventoId));
        if (!evento.getVersion().equals(versaoEsperada)) {
            throw new VersaoEventoDivergenteException();
        }
        return evento;
    }

    private void registrarTransicao(
            Evento evento, String acao, String tipoEvento,
            Map<String, Object> anterior, Long versaoAnterior) {
        Map<String, Object> posterior = snapshot(evento);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                evento.getOrganizacaoId(), acao, RECURSO, evento.getId(),
                evento.getId(), null, null, anterior, posterior,
                versaoAnterior, evento.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                tipoEvento, evento.getOrganizacaoId(), RECURSO, evento.getId(),
                evento.getVersion(), null, 1, posterior));
    }

    private static void validarConfirmacao(
            PublicarEventoRequest request, String operacao) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException(
                    "A confirmacao do " + operacao + " e obrigatoria.");
        }
    }

    private static void validarEncerramentoOperacional(
            EncerrarOperacaoEventoRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException(
                    "A confirmacao do encerramento da operacao e obrigatoria.");
        }
        if (!Boolean.TRUE.equals(request.financialPendingAcknowledged())) {
            throw new IllegalArgumentException(
                    "A pendencia do fechamento financeiro deve ser reconhecida.");
        }
    }

    private static DadosCriacao normalizar(CriarEventoRequest request) {
        if (request == null) throw new IllegalArgumentException("O evento e obrigatorio.");
        String nome = Evento.textoObrigatorio(request.name(), "nome", 180);
        String fuso = Evento.validarFuso(request.timeZone());
        String externalId = Evento.textoOpcional(request.externalId(), "externalId", 160);
        OffsetDateTime inicio = request.startsAt();
        OffsetDateTime fim = request.endsAt();
        if (inicio == null || fim == null || !fim.toInstant().isAfter(inicio.toInstant())) {
            throw new IllegalArgumentException("O fim do evento deve ser posterior ao inicio.");
        }
        if (request.venueId() == null) {
            throw new IllegalArgumentException("O venue e obrigatorio.");
        }
        return new DadosCriacao(
                nome, request.venueId(), inicio, fim, fuso, externalId);
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

    private static String hash(DadosCriacao dados) {
        String canonico = campo(dados.nome()) + campo(dados.venueId().toString())
                + campo(dados.inicio().toInstant().toString())
                + campo(dados.fim().toInstant().toString())
                + campo(dados.fusoHorario()) + campo(dados.externalId());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static String campo(String valor) {
        return valor == null ? "-1:" : valor.length() + ":" + valor;
    }

    private static Map<String, Object> snapshot(Evento evento) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", evento.getNome());
        estado.put("venueId", evento.getVenueId());
        estado.put("inicio", evento.getInicio().toInstant().toString());
        estado.put("fim", evento.getFim().toInstant().toString());
        estado.put("fusoHorario", evento.getFusoHorario());
        estado.put("status", evento.getStatus().name());
        if (evento.getPoliticaReentrada() != null) {
            estado.put("politicaReentrada", evento.getPoliticaReentrada().name());
        }
        if (evento.getExternalId() != null) estado.put("externalId", evento.getExternalId());
        return estado;
    }

    private record DadosCriacao(
            String nome, Long venueId, OffsetDateTime inicio, OffsetDateTime fim,
            String fusoHorario, String externalId) {}
}
