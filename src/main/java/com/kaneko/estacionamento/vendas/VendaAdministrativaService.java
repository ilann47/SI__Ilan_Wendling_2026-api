package com.kaneko.estacionamento.vendas;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.vendas.dto.ItemVendaAdministrativaRequest;
import com.kaneko.estacionamento.vendas.dto.VendaAdministrativaRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendaAdministrativaService {
    private static final String RECURSO = "VENDA_ADMINISTRATIVA";
    private static final String OPERACAO_CRIAR = "CRIAR_VENDA_ADMINISTRATIVA";
    private static final BigDecimal CEM = new BigDecimal("100");

    private final VendaAdministrativaRepository vendas;
    private final ClienteRepository clientes;
    private final ProdutoRepository produtos;
    private final LocalEstoqueRepository locais;
    private final CondicaoPagamentoRepository condicoes;
    private final ContaReceberRepository contas;
    private final EstoqueService estoque;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public VendaAdministrativaService(VendaAdministrativaRepository vendas,
            ClienteRepository clientes, ProdutoRepository produtos,
            LocalEstoqueRepository locais, CondicaoPagamentoRepository condicoes,
            ContaReceberRepository contas, EstoqueService estoque,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.vendas = vendas; this.clientes = clientes; this.produtos = produtos;
        this.locais = locais; this.condicoes = condicoes; this.contas = contas;
        this.estoque = estoque; this.idempotencia = idempotencia;
        this.auditoria = auditoria; this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Page<VendaAdministrativa> listar(Long organizacaoId, Pageable pageable) {
        Page<Long> ids = vendas.findPageIdsByOrganizacaoId(organizacaoId, pageable);
        if (ids.isEmpty()) return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        Map<Long, VendaAdministrativa> porId = new LinkedHashMap<>();
        vendas.findAllByOrganizacaoIdAndIdIn(organizacaoId, ids.getContent())
                .forEach(v -> porId.put(v.getId(), v));
        return new PageImpl<>(ids.getContent().stream().map(porId::get).toList(),
                pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public VendaAdministrativa buscar(Long organizacaoId, Long id) {
        return vendas.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "VendaAdministrativa", id));
    }

    @Transactional
    public VendaAdministrativa criar(Long organizacaoId, Long atorId, String chave,
            VendaAdministrativaRequest request) {
        String idempotencyKey = validarChave(chave);
        String hash = hash(request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, idempotencyKey);
        var anterior = idempotencia.buscar(
                organizacaoId, atorId, OPERACAO_CRIAR, idempotencyKey);
        if (anterior.isPresent()) {
            if (!hash.equals(anterior.get().requestHash()))
                throw new ConflitoException("A chave de idempotencia ja foi usada com outro payload.");
            return buscar(organizacaoId, anterior.get().recursoId());
        }
        String numero = request.numero().trim().toUpperCase(Locale.ROOT);
        if (vendas.existsByOrganizacaoIdAndNumeroIgnoreCase(organizacaoId, numero))
            throw new ConflitoException("Ja existe uma venda com este numero.");
        VendaAdministrativa venda = new VendaAdministrativa();
        venda.setOrganizacaoId(organizacaoId);
        preencher(venda, request, numero);
        venda = vendas.saveAndFlush(venda);
        idempotencia.registrar(organizacaoId, atorId, OPERACAO_CRIAR, idempotencyKey,
                hash, RECURSO, venda.getId());
        registrar(venda, "VENDA_ADMINISTRATIVA_CRIADA", null, snapshot(venda), null);
        publicar(venda, "VendaAdministrativaCriada");
        return venda;
    }

    @Transactional
    public VendaAdministrativa atualizar(Long organizacaoId, Long id, long versao,
            VendaAdministrativaRequest request) {
        VendaAdministrativa venda = bloquear(organizacaoId, id); exigirVersao(venda, versao);
        if (venda.getStatus() != StatusVendaAdministrativa.RASCUNHO)
            throw new ConflitoException("Somente uma venda em rascunho pode ser alterada.");
        String numero = request.numero().trim().toUpperCase(Locale.ROOT);
        if (vendas.existsByOrganizacaoIdAndNumeroIgnoreCaseAndIdNot(
                organizacaoId, numero, id))
            throw new ConflitoException("Ja existe uma venda com este numero.");
        Map<String,Object> anterior = snapshot(venda);
        preencher(venda, request, numero);
        venda.setUpdatedAt(OffsetDateTime.now());
        venda = vendas.saveAndFlush(venda);
        registrar(venda, "VENDA_ADMINISTRATIVA_ATUALIZADA", anterior,
                snapshot(venda), null);
        return venda;
    }

    @Transactional
    public VendaAdministrativa confirmar(Long organizacaoId, Long atorId, Long id,
            long versao) {
        VendaAdministrativa venda = bloquear(organizacaoId, id); exigirVersao(venda, versao);
        Map<String,Object> anterior = snapshot(venda);
        venda.validarConfirmacao();
        for (ItemVendaAdministrativa item : venda.getItens()) {
            estoque.registrarBaixa(organizacaoId, atorId, item.getProduto().getId(),
                    venda.getLocalEstoque().getId(), item.getQuantidade(),
                    item.getProduto().getCusto(), RECURSO,
                    venda.getId() + ":" + item.getSequencia(),
                    "Confirmacao da venda " + venda.getNumero());
        }
        venda.confirmar();
        gerarTitulos(venda);
        venda = vendas.saveAndFlush(venda);
        registrar(venda, "VENDA_ADMINISTRATIVA_CONFIRMADA", anterior,
                snapshot(venda), null);
        publicar(venda, "VendaAdministrativaConfirmada");
        return venda;
    }

    @Transactional
    public VendaAdministrativa cancelar(Long organizacaoId, Long id, long versao,
            String motivo) {
        VendaAdministrativa venda = bloquear(organizacaoId, id); exigirVersao(venda, versao);
        Map<String,Object> anterior = snapshot(venda);
        venda.cancelar(motivo == null ? null : motivo.trim());
        venda = vendas.saveAndFlush(venda);
        registrar(venda, "VENDA_ADMINISTRATIVA_CANCELADA", anterior,
                snapshot(venda), motivo);
        publicar(venda, "VendaAdministrativaCancelada");
        return venda;
    }

    private void preencher(VendaAdministrativa venda, VendaAdministrativaRequest req,
            String numero) {
        var cliente = clientes.findByIdAndOrganizacaoId(req.clienteId(), venda.getOrganizacaoId())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        var local = locais.findAtivoParaMovimento(req.localEstoqueId(), venda.getOrganizacaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LocalEstoque", req.localEstoqueId()));
        CondicaoPagamento condicao = req.condicaoPagamentoId() == null ? null
                : condicoes.findByIdAndOrganizacaoId(req.condicaoPagamentoId(),
                        venda.getOrganizacaoId()).orElseThrow(() ->
                        new RecursoNaoEncontradoException("CondicaoPagamento",
                                req.condicaoPagamentoId()));
        String moeda = req.moeda() == null || req.moeda().isBlank()
                ? "BRL" : req.moeda().trim().toUpperCase(Locale.ROOT);
        try { Currency.getInstance(moeda); } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 valido.");
        }
        venda.setNumero(numero); venda.setCliente(cliente); venda.setLocalEstoque(local);
        venda.setCondicaoPagamento(condicao); venda.setDataEmissao(req.dataEmissao() == null
                ? LocalDate.now() : req.dataEmissao()); venda.setMoeda(moeda);
        venda.setObservacao(req.observacao() == null || req.observacao().isBlank()
                ? null : req.observacao().trim());
        Map<Long,ItemVendaAdministrativa> existentes = new LinkedHashMap<>();
        venda.getItens().forEach(i -> existentes.put(i.getProduto().getId(), i));
        Set<Long> informados = new HashSet<>(); BigDecimal subtotal = BigDecimal.ZERO;
        int sequencia = 1;
        for (ItemVendaAdministrativaRequest ir : req.itens()) {
            if (!informados.add(ir.produtoId()))
                throw new IllegalArgumentException("Um produto nao pode ser repetido na venda.");
            Produto produto = produtos.findByIdAndOrganizacaoId(ir.produtoId(),
                    venda.getOrganizacaoId()).filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", ir.produtoId()));
            BigDecimal desconto = ir.valorDesconto() == null ? BigDecimal.ZERO : ir.valorDesconto();
            BigDecimal total = ir.quantidade().multiply(ir.valorUnitario()).subtract(desconto)
                    .setScale(2, RoundingMode.HALF_UP);
            if (total.signum() < 0) throw new ConflitoException(
                    "O desconto do item nao pode exceder seu valor.");
            ItemVendaAdministrativa item = existentes.get(produto.getId());
            boolean novo = item == null; if (novo) item = new ItemVendaAdministrativa();
            item.setProduto(produto); item.setSequencia(sequencia++);
            item.setQuantidade(ir.quantidade()); item.setValorUnitario(ir.valorUnitario());
            item.setValorDesconto(desconto); item.setValorTotal(total);
            if (novo) venda.adicionarItem(item); subtotal = subtotal.add(total);
        }
        venda.getItens().removeIf(i -> !informados.contains(i.getProduto().getId()));
        venda.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        BigDecimal desconto = req.valorDesconto() == null ? BigDecimal.ZERO : req.valorDesconto();
        BigDecimal total = venda.getSubtotal().subtract(desconto).setScale(2, RoundingMode.HALF_UP);
        if (total.signum() < 0) throw new ConflitoException(
                "O desconto da venda nao pode exceder seu valor.");
        venda.setValorDesconto(desconto); venda.setValorTotal(total);
    }

    private void gerarTitulos(VendaAdministrativa venda) {
        List<ParcelaCondicaoPagamento> parcelas = venda.getCondicaoPagamento() == null
                ? List.of() : venda.getCondicaoPagamento().getParcelas().stream()
                .sorted(Comparator.comparing(ParcelaCondicaoPagamento::getNumero)).toList();
        if (parcelas.isEmpty()) { salvarTitulo(venda, 1, 1, venda.getValorTotal(),
                venda.getDataEmissao(), null); return; }
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i=0; i<parcelas.size(); i++) {
            var parcela = parcelas.get(i);
            BigDecimal valor = i == parcelas.size()-1 ? venda.getValorTotal().subtract(acumulado)
                    : venda.getValorTotal().multiply(parcela.getPercentual())
                            .divide(CEM, 2, RoundingMode.HALF_UP);
            acumulado = acumulado.add(valor);
            salvarTitulo(venda, i+1, parcelas.size(), valor,
                    venda.getDataEmissao().plusDays(parcela.getDias()),
                    parcela.getFormaPagamento());
        }
    }

    private void salvarTitulo(VendaAdministrativa venda, int numero, int total,
            BigDecimal valor, LocalDate vencimento,
            com.kaneko.estacionamento.pagamento.FormaPagamento forma) {
        ContaReceber conta = new ContaReceber(); conta.setOrganizacaoId(venda.getOrganizacaoId());
        conta.setCliente(venda.getCliente()); conta.setVendaAdministrativa(venda);
        conta.setCondicaoPagamento(venda.getCondicaoPagamento()); conta.setFormaPagamento(forma);
        conta.setNumeroParcela(numero); conta.setTotalParcelas(total);
        conta.setValorOriginal(valor); conta.setValorTotal(valor);
        conta.setValorRecebido(BigDecimal.ZERO); conta.setDataEmissao(venda.getDataEmissao());
        conta.setDataVencimento(vencimento); conta.setSituacao(SituacaoContaReceber.PENDENTE);
        contas.save(conta);
    }

    private VendaAdministrativa bloquear(Long org, Long id) {
        return vendas.findParaAtualizar(id, org).orElseThrow(() ->
                new RecursoNaoEncontradoException("VendaAdministrativa", id));
    }
    private static void exigirVersao(VendaAdministrativa venda, long versao) {
        if (venda.getVersion() == null || venda.getVersion() != versao)
            throw new VersaoVendaAdministrativaDivergenteException();
    }
    private void registrar(VendaAdministrativa venda, String acao, Map<String,Object> antes,
            Map<String,Object> depois, String motivo) {
        auditoria.registrar(new RegistrarAuditoriaCommand(venda.getOrganizacaoId(), acao,
                RECURSO, venda.getId(), null, null, motivo, antes, depois, null,
                venda.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
    }
    private void publicar(VendaAdministrativa venda, String tipo) {
        outbox.publicar(new PublicarEventoOutboxCommand(tipo, venda.getOrganizacaoId(),
                RECURSO, venda.getId(), venda.getVersion(), null, 1, snapshot(venda)));
    }
    private static Map<String,Object> snapshot(VendaAdministrativa venda) {
        Map<String,Object> m = new LinkedHashMap<>(); m.put("vendaAdministrativaId", venda.getId());
        m.put("numero", venda.getNumero()); m.put("clienteId", venda.getCliente().getId());
        m.put("status", venda.getStatus().name()); m.put("valorTotal", venda.getValorTotal());
        m.put("quantidadeItens", venda.getItens().size()); return m;
    }
    private static String validarChave(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(
                "O cabecalho Idempotency-Key e obrigatorio.");
        String key=value.trim(); if (key.length()>100) throw new IllegalArgumentException(
                "Idempotency-Key deve ter no maximo 100 caracteres."); return key;
    }
    private static String hash(VendaAdministrativaRequest req) {
        StringBuilder s=new StringBuilder().append(req.numero()).append('|')
                .append(req.clienteId()).append('|').append(req.condicaoPagamentoId())
                .append('|').append(req.localEstoqueId()).append('|').append(req.dataEmissao())
                .append('|').append(req.moeda()).append('|').append(req.valorDesconto());
        req.itens().forEach(i -> s.append('|').append(i.produtoId()).append(':')
                .append(i.quantidade()).append(':').append(i.valorUnitario()).append(':')
                .append(i.valorDesconto()));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(s.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
