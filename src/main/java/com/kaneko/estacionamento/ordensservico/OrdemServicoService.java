package com.kaneko.estacionamento.ordensservico;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import com.kaneko.estacionamento.ordensservico.dto.OrdemServicoResponse;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.ordensservico.dto.ItemOrdemServicoRequest;
import com.kaneko.estacionamento.ordensservico.dto.OrdemServicoRequest;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdemServicoService {
    private static final String RECURSO = "ORDEM_SERVICO";
    private static final String OPERACAO_CRIAR = "CRIAR_ORDEM_SERVICO";
    private static final String PREFIXO_PATIO = "PATIO-";

    private final OrdemServicoRepository ordens;
    private final ClienteRepository clientes;
    private final ServicoRepository servicos;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public OrdemServicoService(OrdemServicoRepository ordens, ClienteRepository clientes,
            ServicoRepository servicos, IdempotenciaRepository idempotencia,
            AuditoriaService auditoria, OutboxService outbox) {
        this.ordens = ordens;
        this.clientes = clientes;
        this.servicos = servicos;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Page<OrdemServico> listar(Long organizacaoId, Pageable pageable) {
        return listar(organizacaoId, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrdemServico> listar(Long organizacaoId, String numero,
            StatusOrdemServico status, Pageable pageable) {
        String termo = numero == null ? "" : numero.trim().toLowerCase(Locale.ROOT);
        Page<Long> ids = termo.isEmpty() && status == null
                ? ordens.findPageIdsByOrganizacaoId(organizacaoId, pageable)
                : ordens.findPageIdsFiltrados(organizacaoId, termo,
                        status == null ? List.of(StatusOrdemServico.values()) : List.of(status), pageable);
        if (ids.isEmpty()) return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        Map<Long, OrdemServico> porId = new LinkedHashMap<>();
        ordens.findAllByOrganizacaoIdAndIdIn(organizacaoId, ids.getContent())
                .forEach(o -> porId.put(o.getId(), o));
        return new PageImpl<>(ids.getContent().stream().map(porId::get).toList(),
                pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrdemServico buscar(Long organizacaoId, Long id) {
        return ordens.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OrdemServico", id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long abrirParaEstadia(long org, long movimento, OffsetDateTime entrada, BigDecimal valor, Servico servico) {
        var anterior = ordens.findByOrganizacaoIdAndMovimentacaoPatioId(org, movimento);
        if (anterior.isPresent()) return anterior.get().getId();
        String numero = PREFIXO_PATIO + movimento;
        if (ordens.existsByOrganizacaoIdAndNumeroIgnoreCase(org, numero)) {
            numero += "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        var ordem = new OrdemServico();
        ordem.setOrganizacaoId(org);
        ordem.setMovimentacaoPatioId(movimento);
        ordem.setNumero(numero);
        ordem.setDataAbertura(entrada.toLocalDate());
        ordem.setSubtotal(valor);
        ordem.setValorTotal(valor);
        var item = new ItemOrdemServico();
        item.setSequencia(1);
        item.setServico(servico);
        item.setDescricaoSnapshot(servico == null ? "Estacionamento de evento" : servico.getNome());
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(valor);
        item.setValorTotal(valor);
        ordem.adicionarItem(item);
        ordem.iniciar(entrada);
        ordem = ordens.saveAndFlush(ordem);
        registrar(ordem, "ORDEM_SERVICO_PATIO_ABERTA", null, snapshot(ordem), null);
        publicar(ordem, "OrdemServicoIniciada");
        return ordem.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void concluirParaEstadia(long org, long movimento, long id, OffsetDateTime saida) {
        var ordem = bloquear(org, id);
        if (ordem.getMovimentacaoPatioId() == null || ordem.getMovimentacaoPatioId() != movimento) {
            throw new ConflitoException("A ordem não pertence a esta estadia.");
        }
        var antes = snapshot(ordem);
        ordem.concluir(saida);
        ordem = ordens.saveAndFlush(ordem);
        registrar(ordem, "ORDEM_SERVICO_CONCLUIDA", antes, snapshot(ordem), null);
        publicar(ordem, "OrdemServicoConcluida");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelarParaEstadia(long org,long movimento,long id,String motivo) {
        var ordem=bloquear(org,id);
        if(ordem.getMovimentacaoPatioId()==null || ordem.getMovimentacaoPatioId()!=movimento)
            throw new ConflitoException("A ordem não pertence a esta estadia.");
        if(ordem.getStatus()==StatusOrdemServico.CANCELADA)return;
        var antes=snapshot(ordem);
        ordem.setStatus(StatusOrdemServico.CANCELADA);
        ordem.setMotivoCancelamento(motivo);
        ordem=ordens.saveAndFlush(ordem);
        registrar(ordem,"ORDEM_SERVICO_PATIO_CANCELADA",antes,snapshot(ordem),motivo);
        publicar(ordem,"OrdemServicoPatioCancelada");
    }

    @Transactional
    public OrdemServico criar(Long organizacaoId, Long atorId, String chave,
            OrdemServicoRequest request) {
        String key = validarChave(chave);
        String requestHash = hash(request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, key);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_CRIAR, key);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException("A chave de idempotencia ja foi usada com outro payload.");
            }
            return buscar(organizacaoId, anterior.get().recursoId());
        }
        String numero = normalizarNumero(request.numero());
        if (ordens.existsByOrganizacaoIdAndNumeroIgnoreCase(organizacaoId, numero)) {
            throw new ConflitoException("Ja existe uma ordem de servico com este numero.");
        }
        OrdemServico ordem = new OrdemServico();
        ordem.setOrganizacaoId(organizacaoId);
        preencher(ordem, request, numero);
        ordem = ordens.saveAndFlush(ordem);
        idempotencia.registrar(organizacaoId, atorId, OPERACAO_CRIAR, key,
                requestHash, RECURSO, ordem.getId());
        registrar(ordem, "ORDEM_SERVICO_CRIADA", null, snapshot(ordem), null);
        publicar(ordem, "OrdemServicoCriada");
        return ordem;
    }

    @Transactional
    public OrdemServico atualizar(Long organizacaoId, Long id, long versao,
            OrdemServicoRequest request) {
        OrdemServico ordem = bloquear(organizacaoId, id);
        exigirOperacaoManual(ordem);
        exigirVersao(ordem, versao);
        if (ordem.getStatus() != StatusOrdemServico.RASCUNHO) {
            throw new ConflitoException("Somente uma ordem em rascunho pode ser alterada.");
        }
        String numero = normalizarNumero(request.numero());
        if (ordens.existsByOrganizacaoIdAndNumeroIgnoreCaseAndIdNot(
                organizacaoId, numero, id)) {
            throw new ConflitoException("Ja existe uma ordem de servico com este numero.");
        }
        Map<String, Object> anterior = snapshot(ordem);
        preencher(ordem, request, numero);
        ordem.setUpdatedAt(OffsetDateTime.now());
        ordem = ordens.saveAndFlush(ordem);
        registrar(ordem, "ORDEM_SERVICO_ATUALIZADA", anterior, snapshot(ordem), null);
        return ordem;
    }

    @Transactional
    public OrdemServicoResponse iniciar(Long organizacaoId, Long id, long versao) {
        OrdemServico ordem = bloquear(organizacaoId, id);
        exigirOperacaoManual(ordem);
        exigirVersao(ordem, versao);
        Map<String, Object> anterior = snapshot(ordem);
        ordem.iniciar();
        ordem = ordens.saveAndFlush(ordem);
        registrar(ordem, "ORDEM_SERVICO_INICIADA", anterior, snapshot(ordem), null);
        publicar(ordem, "OrdemServicoIniciada");
        return OrdemServicoResponse.from(ordem);
    }

    @Transactional
    public OrdemServicoResponse concluir(Long organizacaoId, Long id, long versao) {
        OrdemServico ordem = bloquear(organizacaoId, id);
        exigirOperacaoManual(ordem);
        exigirVersao(ordem, versao);
        Map<String, Object> anterior = snapshot(ordem);
        ordem.concluir();
        ordem = ordens.saveAndFlush(ordem);
        registrar(ordem, "ORDEM_SERVICO_CONCLUIDA", anterior, snapshot(ordem), null);
        publicar(ordem, "OrdemServicoConcluida");
        return OrdemServicoResponse.from(ordem);
    }

    @Transactional
    public OrdemServicoResponse cancelar(Long organizacaoId, Long id, long versao, String motivo) {
        OrdemServico ordem = bloquear(organizacaoId, id);
        exigirOperacaoManual(ordem);
        exigirVersao(ordem, versao);
        Map<String, Object> anterior = snapshot(ordem);
        ordem.cancelar(motivo);
        ordem = ordens.saveAndFlush(ordem);
        registrar(ordem, "ORDEM_SERVICO_CANCELADA", anterior, snapshot(ordem), motivo);
        publicar(ordem, "OrdemServicoCancelada");
        return OrdemServicoResponse.from(ordem);
    }

    private void preencher(OrdemServico ordem, OrdemServicoRequest req, String numero) {
        var cliente = clientes.findByIdAndOrganizacaoId(req.clienteId(), ordem.getOrganizacaoId())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        String moeda = req.moeda() == null || req.moeda().isBlank()
                ? "BRL" : req.moeda().trim().toUpperCase(Locale.ROOT);
        try { Currency.getInstance(moeda); } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 valido.");
        }
        LocalDate abertura = req.dataAbertura() == null ? LocalDate.now() : req.dataAbertura();
        if (req.previsaoConclusao() != null && req.previsaoConclusao().isBefore(abertura)) {
            throw new IllegalArgumentException("A previsao de conclusao nao pode anteceder a abertura.");
        }
        ordem.setNumero(numero);
        ordem.setCliente(cliente);
        ordem.setDataAbertura(abertura);
        ordem.setPrevisaoConclusao(req.previsaoConclusao());
        ordem.setMoeda(moeda);
        ordem.setObservacao(req.observacao() == null || req.observacao().isBlank()
                ? null : req.observacao().trim());

        Map<Long, ItemOrdemServico> existentes = new LinkedHashMap<>();
        ordem.getItens().forEach(i -> existentes.put(i.getServico().getId(), i));
        Set<Long> informados = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int sequencia = 1;
        for (ItemOrdemServicoRequest ir : req.itens()) {
            if (!informados.add(ir.servicoId())) {
                throw new IllegalArgumentException("Um servico nao pode ser repetido na ordem.");
            }
            Servico servico = servicos.findByIdAndOrganizacaoId(ir.servicoId(),
                    ordem.getOrganizacaoId()).orElseThrow(() ->
                    new RecursoNaoEncontradoException("Servico", ir.servicoId()));
            BigDecimal desconto = ir.valorDesconto() == null
                    ? BigDecimal.ZERO : ir.valorDesconto();
            BigDecimal total = ir.quantidade().multiply(ir.valorUnitario())
                    .setScale(MilionesimosConverter.ESCALA, RoundingMode.HALF_UP).subtract(desconto);
            if (total.signum() < 0) {
                throw new ConflitoException("O desconto do item nao pode exceder seu valor.");
            }
            if (total.compareTo(new BigDecimal("9999999999999.999999")) > 0) {
                throw new ConflitoException("O valor do item excede o limite da ordem.");
            }
            ItemOrdemServico item = existentes.get(servico.getId());
            boolean novo = item == null;
            if (novo) item = new ItemOrdemServico();
            item.setServico(servico);
            item.setSequencia(sequencia++);
            item.setDescricaoSnapshot(servico.getNome());
            item.setQuantidade(ir.quantidade());
            item.setValorUnitario(ir.valorUnitario());
            item.setValorDesconto(desconto);
            item.setValorTotal(total);
            if (novo) ordem.adicionarItem(item);
            subtotal = subtotal.add(total);
        }
        ordem.getItens().removeIf(i -> !informados.contains(i.getServico().getId()));
        if (subtotal.compareTo(new BigDecimal("9999999999999.999999")) > 0) {
            throw new ConflitoException("O subtotal excede o limite da ordem.");
        }
        ordem.setSubtotal(subtotal);
        BigDecimal desconto = req.valorDesconto() == null
                ? BigDecimal.ZERO : req.valorDesconto();
        BigDecimal total = ordem.getSubtotal().subtract(desconto);
        if (total.signum() < 0) {
            throw new ConflitoException("O desconto da ordem nao pode exceder seu valor.");
        }
        ordem.setValorDesconto(desconto);
        ordem.setValorTotal(total);
    }

    private OrdemServico bloquear(Long org, Long id) {
        return ordens.findParaAtualizar(id, org).orElseThrow(() ->
                new RecursoNaoEncontradoException("OrdemServico", id));
    }
    private static void exigirVersao(OrdemServico ordem, long versao) {
        if (ordem.getVersion() == null || ordem.getVersion() != versao) {
            throw new VersaoOrdemServicoDivergenteException();
        }
    }
    private static void exigirOperacaoManual(OrdemServico ordem) {
        if (ordem.getMovimentacaoPatioId() != null) {
            throw new ConflitoException("Esta ordem é controlada automaticamente pelo Pátio.");
        }
    }
    private void registrar(OrdemServico ordem, String acao, Map<String, Object> antes,
            Map<String, Object> depois, String motivo) {
        auditoria.registrar(new RegistrarAuditoriaCommand(ordem.getOrganizacaoId(), acao,
                RECURSO, ordem.getId(), null, null, motivo, antes, depois, null,
                ordem.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
    }
    private void publicar(OrdemServico ordem, String tipo) {
        outbox.publicar(new PublicarEventoOutboxCommand(tipo, ordem.getOrganizacaoId(),
                RECURSO, ordem.getId(), ordem.getVersion(), null, 1, snapshot(ordem)));
    }
    private static Map<String, Object> snapshot(OrdemServico ordem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ordemServicoId", ordem.getId());
        m.put("numero", ordem.getNumero());
        if (ordem.getCliente() != null) m.put("clienteId", ordem.getCliente().getId());
        if (ordem.getMovimentacaoPatioId() != null) m.put("movimentacaoPatioId", ordem.getMovimentacaoPatioId());
        m.put("status", ordem.getStatus().name());
        m.put("valorTotal", ordem.getValorTotal());
        m.put("quantidadeItens", ordem.getItens().size());
        return m;
    }
    private static String normalizarNumero(String numero) {
        String normalizado = numero.trim().toUpperCase(Locale.ROOT);
        if (normalizado.startsWith(PREFIXO_PATIO)) {
            throw new ConflitoException("O prefixo PATIO- é reservado às ordens automáticas do pátio.");
        }
        return normalizado;
    }
    private static String validarChave(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O cabecalho Idempotency-Key e obrigatorio.");
        }
        String key = value.trim();
        if (key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return key;
    }
    private static String hash(OrdemServicoRequest req) {
        StringBuilder value = new StringBuilder().append(req.numero()).append('|')
                .append(req.clienteId()).append('|').append(req.dataAbertura()).append('|')
                .append(req.previsaoConclusao()).append('|').append(req.moeda()).append('|')
                .append(req.valorDesconto()).append('|').append(req.observacao());
        req.itens().forEach(i -> value.append('|').append(i.servicoId()).append(':')
                .append(i.quantidade()).append(':').append(i.valorUnitario()).append(':')
                .append(i.valorDesconto()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
