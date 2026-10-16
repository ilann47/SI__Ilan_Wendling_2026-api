package com.kaneko.estacionamento.inventario.produto;

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
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEvento;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import com.kaneko.estacionamento.inventario.produto.dto.CriarProdutoEstacionamentoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.ProdutoEstacionamentoResponse;
import com.kaneko.estacionamento.inventario.produto.dto.PublicarProdutoRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoEstacionamentoService {
    private static final String OPERACAO_CRIAR = "PRODUTO_ESTACIONAMENTO_CRIAR";
    private static final String RECURSO = "PRODUTO_ESTACIONAMENTO";

    private final ProdutoEstacionamentoRepository produtos;
    private final EventoRepository eventos;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final LotePrecoRepository lotes;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public ProdutoEstacionamentoService(
            ProdutoEstacionamentoRepository produtos, EventoRepository eventos,
            AlocacaoPatioEventoRepository alocacoes, LotePrecoRepository lotes,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.produtos = produtos;
        this.eventos = eventos;
        this.alocacoes = alocacoes;
        this.lotes = lotes;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<ProdutoEstacionamentoResponse> listar(Long organizacaoId, Long eventoId) {
        eventos.findByIdAndOrganizacaoId(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        return produtos.findAllByOrganizacaoIdAndEventoIdOrderById(organizacaoId, eventoId)
                .stream().map(ProdutoEstacionamentoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProdutoEstacionamentoResponse buscar(Long organizacaoId, Long produtoId) {
        return ProdutoEstacionamentoResponse.from(
                produtos.findByIdAndOrganizacaoId(produtoId, organizacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Produto de estacionamento", produtoId)));
    }

    @Transactional
    public ProdutoEstacionamentoResponse criar(
            Long organizacaoId, Long atorId, Long eventoId, String chaveIdempotencia,
            CriarProdutoEstacionamentoRequest request) {
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
            return ProdutoEstacionamentoResponse.from(
                    produtos.findByIdAndOrganizacaoId(anterior.get().recursoId(), organizacaoId)
                            .orElseThrow(() -> new ConflitoException(
                                    "O resultado idempotente nao esta mais disponivel.")));
        }

        validarEventoConfiguravel(eventoId, organizacaoId);
        AlocacaoPatioEvento alocacao = alocacoes
                .findByIdAndOrganizacaoIdAndEventoIdForUpdate(
                        request.parkingAllocationId(), organizacaoId, eventoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Alocacao do evento", request.parkingAllocationId()));
        validarJanela(alocacao, request.accessStartsAt(), request.accessEndsAt());
        if (produtos.existeNomeNormalizado(eventoId, request.name())) {
            throw new ConflitoException("Ja existe produto com este nome no evento.");
        }
        long quotaComprometida = produtos.somarQuotasPorAlocacao(alocacao.getId());
        if (quotaComprometida + request.quota() > alocacao.getCapacidadeVendavel()) {
            throw new ConflitoException(
                    "A soma das quotas excede a capacidade vendavel da alocacao.");
        }

        ProdutoEstacionamento produto = ProdutoEstacionamento.criar(
                organizacaoId, eventoId, alocacao.getId(), request.name(), request.category(),
                request.right(), request.accessStartsAt(), request.accessEndsAt(), request.quota(),
                request.benefits(), request.restrictions());
        produto = produtos.saveAndFlush(produto);
        Map<String, Object> estado = snapshot(produto);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "PRODUTO_ESTACIONAMENTO_CRIADO", RECURSO, produto.getId(),
                eventoId, alocacao.getPatioId(), null, null, estado, null, produto.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave,
                requestHash, RECURSO, produto.getId());
        return ProdutoEstacionamentoResponse.from(produto);
    }

    @Transactional
    public ProdutoEstacionamentoResponse publicar(
            Long organizacaoId, Long produtoId, long versaoEsperada,
            PublicarProdutoRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException("A confirmacao da publicacao e obrigatoria.");
        }
        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                        produtoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", produtoId));
        if (!produto.getVersion().equals(versaoEsperada)) {
            throw new VersaoProdutoDivergenteException();
        }
        if (!lotes.existsByProdutoId(produtoId)) {
            throw new ConflitoException(
                    "O produto precisa possuir ao menos um lote de preco para ser publicado.");
        }
        Map<String, Object> anterior = snapshot(produto);
        Long versaoAnterior = produto.getVersion();
        produto.publicar();
        produtos.flush();
        Map<String, Object> posterior = snapshot(produto);
        Long patioId = alocacoes.findByIdAndOrganizacaoId(
                        produto.getAlocacaoId(), organizacaoId)
                .map(AlocacaoPatioEvento::getPatioId)
                .orElse(null);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "PRODUTO_ESTACIONAMENTO_PUBLICADO", RECURSO,
                produto.getId(), produto.getEventoId(), patioId, null,
                anterior, posterior, versaoAnterior, produto.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "ProdutoEstacionamentoPublicado", organizacaoId, RECURSO,
                produto.getId(), produto.getVersion(), null, 1, posterior));
        return ProdutoEstacionamentoResponse.from(produto);
    }

    private Evento validarEventoConfiguravel(Long eventoId, Long organizacaoId) {
        Evento evento = eventos.findByIdAndOrganizacaoId(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        if (evento.getStatus() != StatusEvento.RASCUNHO
                && evento.getStatus() != StatusEvento.CONFIGURADO) {
            throw new ConflitoException("O estado do evento nao permite criar produtos.");
        }
        return evento;
    }

    private static void validarJanela(
            AlocacaoPatioEvento alocacao,
            java.time.OffsetDateTime inicio, java.time.OffsetDateTime fim) {
        boolean contida = !inicio.toInstant().isBefore(alocacao.getInicio().toInstant())
                && !fim.toInstant().isAfter(alocacao.getFim().toInstant());
        if (!contida) {
            throw new IllegalArgumentException(
                    "A janela de acesso deve estar contida no periodo da alocacao.");
        }
    }

    private static void validarRequest(CriarProdutoEstacionamentoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("O produto e obrigatorio.");
        }
        if (request.parkingAllocationId() == null) {
            throw new IllegalArgumentException("A alocacao e obrigatoria.");
        }
        ProdutoEstacionamento.validarDados(
                request.name(), request.category(), request.right(),
                request.accessStartsAt(), request.accessEndsAt(), request.quota(),
                request.benefits(), request.restrictions());
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O cabecalho Idempotency-Key e obrigatorio.");
        }
        String chave = valor.trim();
        if (chave.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return chave;
    }

    private static String hash(Long eventoId, CriarProdutoEstacionamentoRequest request) {
        String canonico = campo(eventoId)
                + campo(request.parkingAllocationId())
                + campo(request.name().trim())
                + campo(request.category())
                + campo(request.right())
                + campo(request.accessStartsAt().toInstant())
                + campo(request.accessEndsAt().toInstant())
                + campo(request.quota())
                + campo(codigosCanonicos(request.benefits()))
                + campo(codigosCanonicos(request.restrictions()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static List<String> codigosCanonicos(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        List<String> resultado = new ArrayList<>();
        for (String valor : valores) {
            resultado.add(valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT));
        }
        resultado.sort(Comparator.naturalOrder());
        return resultado;
    }

    private static String campo(Object valor) {
        String texto = String.valueOf(valor);
        return texto.length() + ":" + texto;
    }

    private static Map<String, Object> snapshot(ProdutoEstacionamento produto) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("eventoId", produto.getEventoId());
        estado.put("alocacaoId", produto.getAlocacaoId());
        estado.put("nome", produto.getNome());
        estado.put("categoria", produto.getCategoria().name());
        estado.put("direito", produto.getDireito().name());
        estado.put("inicioAcesso", produto.getInicioAcesso().toInstant().toString());
        estado.put("fimAcesso", produto.getFimAcesso().toInstant().toString());
        estado.put("quota", produto.getQuota());
        estado.put("status", produto.getStatus().name());
        estado.put("beneficios", List.copyOf(produto.getBeneficios()));
        estado.put("restricoes", List.copyOf(produto.getRestricoes()));
        return estado;
    }
}
