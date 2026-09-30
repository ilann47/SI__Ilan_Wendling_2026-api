package com.kaneko.estacionamento.compras;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.compras.dto.ItemOrdemCompraRequest;
import com.kaneko.estacionamento.compras.dto.ItemRecebimentoCompraRequest;
import com.kaneko.estacionamento.compras.dto.OrdemCompraRequest;
import com.kaneko.estacionamento.compras.dto.ReceberOrdemCompraRequest;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoFornecedorRepository;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.logistica.Transportadora;
import com.kaneko.estacionamento.logistica.TransportadoraRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdemCompraService {

    private static final String RECURSO_ORDEM = "ORDEM_COMPRA";
    private static final String RECURSO_RECEBIMENTO = "RECEBIMENTO_COMPRA";
    private static final String OPERACAO_CRIAR = "CRIAR_ORDEM_COMPRA";
    private static final String OPERACAO_RECEBER = "RECEBER_ORDEM_COMPRA";

    private final OrdemCompraRepository ordens;
    private final RecebimentoCompraRepository recebimentos;
    private final FornecedorRepository fornecedores;
    private final ProdutoRepository produtos;
    private final ProdutoFornecedorRepository produtoFornecedores;
    private final LocalEstoqueRepository locais;
    private final CondicaoPagamentoRepository condicoesPagamento;
    private final TransportadoraRepository transportadoras;
    private final UsuarioRepository usuarios;
    private final EstoqueService estoque;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public OrdemCompraService(OrdemCompraRepository ordens,
            RecebimentoCompraRepository recebimentos,
            FornecedorRepository fornecedores, ProdutoRepository produtos,
            ProdutoFornecedorRepository produtoFornecedores,
            LocalEstoqueRepository locais,
            CondicaoPagamentoRepository condicoesPagamento,
            TransportadoraRepository transportadoras, UsuarioRepository usuarios,
            EstoqueService estoque, IdempotenciaRepository idempotencia,
            AuditoriaService auditoria, OutboxService outbox) {
        this.ordens = ordens;
        this.recebimentos = recebimentos;
        this.fornecedores = fornecedores;
        this.produtos = produtos;
        this.produtoFornecedores = produtoFornecedores;
        this.locais = locais;
        this.condicoesPagamento = condicoesPagamento;
        this.transportadoras = transportadoras;
        this.usuarios = usuarios;
        this.estoque = estoque;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Page<OrdemCompra> listar(Long organizacaoId, Pageable pageable) {
        return listar(organizacaoId, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrdemCompra> listar(Long organizacaoId, String numero,
            List<StatusOrdemCompra> statuses, Pageable pageable) {
        Page<Long> ids = ordens.findPageIdsFiltrados(organizacaoId,
                numero == null ? "" : numero.trim(), statuses == null || statuses.isEmpty()
                        ? List.of(StatusOrdemCompra.values()) : statuses, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<Long, OrdemCompra> porId = new LinkedHashMap<>();
        ordens.findAllByOrganizacaoIdAndIdIn(organizacaoId, ids.getContent())
                .forEach(ordem -> porId.put(ordem.getId(), ordem));
        List<OrdemCompra> pagina = ids.getContent().stream().map(porId::get).toList();
        return new PageImpl<>(pagina, pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrdemCompra buscar(Long organizacaoId, Long id) {
        return ordens.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OrdemCompra", id));
    }

    @Transactional(readOnly = true)
    public List<RecebimentoCompra> listarRecebimentos(Long organizacaoId, Long ordemId) {
        buscar(organizacaoId, ordemId);
        return recebimentos.findAllByOrganizacaoIdAndOrdemCompraIdOrderByRecebidoEmDesc(
                organizacaoId, ordemId);
    }

    @Transactional
    public OrdemCompra criar(Long organizacaoId, Long atorId, String chaveIdempotencia,
            OrdemCompraRequest request) {
        String chave = validarChave(chaveIdempotencia);
        String requestHash = hashRequest(request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        if (anterior.isPresent()) {
            conferirHash(anterior.get().requestHash(), requestHash);
            return buscar(organizacaoId, anterior.get().recursoId());
        }

        String numero = gerarNumeroInterno();
        String numeroNota = normalizarIdentificadorFiscal(request.numeroNota());
        String serieNota = normalizarIdentificadorFiscal(request.serieNota());
        String modeloNota = normalizarIdentificadorFiscal(request.modeloNota());
        exigirChaveFiscalDisponivel(organizacaoId, request.fornecedorId(), numeroNota,
                serieNota, modeloNota, null);
        OrdemCompra ordem = new OrdemCompra();
        ordem.setOrganizacaoId(organizacaoId);
        ordem.setComprador(usuarios.findByIdAndAtivoTrue(atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario", atorId)));
        preencher(ordem, request, numero);
        ordem = ordens.saveAndFlush(ordem);
        idempotencia.registrar(organizacaoId, atorId, OPERACAO_CRIAR, chave,
                requestHash, RECURSO_ORDEM, ordem.getId());
        auditar(ordem, "ORDEM_COMPRA_CRIADA", null, snapshot(ordem), null);
        publicar(ordem, "OrdemCompraCriada", snapshot(ordem));
        return ordem;
    }

    @Transactional
    public OrdemCompra atualizar(Long organizacaoId, Long id, long versaoEsperada,
            OrdemCompraRequest request) {
        OrdemCompra ordem = bloquear(organizacaoId, id);
        exigirVersao(ordem, versaoEsperada);
        if (ordem.getStatus() != StatusOrdemCompra.RASCUNHO) {
            throw new ConflitoException("Somente uma ordem em rascunho pode ser alterada.");
        }
        String numero = ordem.getNumero();
        String numeroNota = normalizarIdentificadorFiscal(request.numeroNota());
        String serieNota = normalizarIdentificadorFiscal(request.serieNota());
        String modeloNota = normalizarIdentificadorFiscal(request.modeloNota());
        if (!ordem.getNumeroNota().equals(numeroNota)
                || !ordem.getSerieNota().equals(serieNota)
                || !ordem.getModeloNota().equals(modeloNota)
                || !ordem.getFornecedor().getId().equals(request.fornecedorId())) {
            throw new ConflitoException(
                    "Fornecedor, numero, serie e modelo da nota nao podem ser alterados apos a inclusao dos produtos.");
        }
        Map<String, Object> anterior = snapshot(ordem);
        preencher(ordem, request, numero);
        ordem = ordens.saveAndFlush(ordem);
        auditar(ordem, "ORDEM_COMPRA_ATUALIZADA", anterior, snapshot(ordem), null);
        return ordem;
    }

    @Transactional
    public OrdemCompra aprovar(Long organizacaoId, Long id, long versaoEsperada) {
        OrdemCompra ordem = bloquear(organizacaoId, id);
        exigirVersao(ordem, versaoEsperada);
        Map<String, Object> anterior = snapshot(ordem);
        ordem.aprovar();
        ordem = ordens.saveAndFlush(ordem);
        auditar(ordem, "ORDEM_COMPRA_APROVADA", anterior, snapshot(ordem), null);
        publicar(ordem, "OrdemCompraAprovada", snapshot(ordem));
        return ordem;
    }

    @Transactional
    public OrdemCompra cancelar(Long organizacaoId, Long id, long versaoEsperada,
            String motivo) {
        OrdemCompra ordem = bloquear(organizacaoId, id);
        exigirVersao(ordem, versaoEsperada);
        Map<String, Object> anterior = snapshot(ordem);
        String motivoNormalizado = motivo == null ? "" : motivo.trim();
        ordem.cancelar(motivoNormalizado);
        ordem = ordens.saveAndFlush(ordem);
        auditar(ordem, "ORDEM_COMPRA_CANCELADA", anterior, snapshot(ordem), motivoNormalizado);
        publicar(ordem, "OrdemCompraCancelada", snapshot(ordem));
        return ordem;
    }

    @Transactional
    public OrdemCompra rejeitarEntrega(Long organizacaoId, Long id, long versaoEsperada,
            String motivo) {
        OrdemCompra ordem = bloquear(organizacaoId, id);
        exigirVersao(ordem, versaoEsperada);
        String motivoNormalizado = normalizarOpcional(motivo);
        if (motivoNormalizado == null) {
            throw new IllegalArgumentException("O motivo da rejeicao e obrigatorio.");
        }
        Map<String, Object> anterior = snapshot(ordem);
        ordem.rejeitarEntrega(motivoNormalizado);
        ordem = ordens.saveAndFlush(ordem);
        auditar(ordem, "ENTREGA_COMPRA_REJEITADA", anterior, snapshot(ordem), motivoNormalizado);
        publicar(ordem, "EntregaCompraRejeitada", snapshot(ordem));
        return ordem;
    }

    @Transactional
    public RecebimentoRegistrado receber(Long organizacaoId, Long atorId, Long ordemId,
            long versaoEsperada, String chaveIdempotencia, ReceberOrdemCompraRequest request) {
        String chave = validarChave(chaveIdempotencia);
        String requestHash = hashRecebimento(ordemId, request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_RECEBER, chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_RECEBER, chave);
        if (anterior.isPresent()) {
            conferirHash(anterior.get().requestHash(), requestHash);
            RecebimentoCompra recebimento = recebimentos
                    .findByIdAndOrganizacaoId(anterior.get().recursoId(), organizacaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "RecebimentoCompra", anterior.get().recursoId()));
            return new RecebimentoRegistrado(
                    buscar(organizacaoId, recebimento.getOrdemCompra().getId()), recebimento);
        }

        OrdemCompra ordem = bloquear(organizacaoId, ordemId);
        exigirVersao(ordem, versaoEsperada);
        ordem.exigirRecebivel();
        LocalEstoque local = locais.findAtivoParaMovimento(
                        request.localEstoqueId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LocalEstoque", request.localEstoqueId()));
        Usuario ator = usuarios.findByIdAndAtivoTrue(atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario", atorId));

        Map<Long, ItemOrdemCompra> itensOrdem = new LinkedHashMap<>();
        ordem.getItens().forEach(item -> itensOrdem.put(item.getId(), item));
        if (request.itens().size() != itensOrdem.size()) {
            throw new ConflitoException(
                    "O recebimento deve contemplar integralmente todos os itens da ordem.");
        }
        Set<Long> itensInformados = new HashSet<>();
        for (ItemRecebimentoCompraRequest item : request.itens()) {
            if (!itensInformados.add(item.itemOrdemCompraId())) {
                throw new IllegalArgumentException("Um item da ordem nao pode ser repetido.");
            }
            ItemOrdemCompra itemOrdem = itensOrdem.get(item.itemOrdemCompraId());
            if (itemOrdem == null) {
                throw new RecursoNaoEncontradoException(
                        "ItemOrdemCompra", item.itemOrdemCompraId());
            }
            if (item.quantidade().compareTo(itemOrdem.saldoReceber()) != 0) {
                throw new ConflitoException(
                        "O recebimento deve informar a quantidade integral de cada item.");
            }
        }

        Map<String, Object> anteriorOrdem = snapshot(ordem);
        RecebimentoCompra recebimento = new RecebimentoCompra();
        recebimento.setOrganizacaoId(organizacaoId);
        recebimento.setOrdemCompra(ordem);
        recebimento.setLocalEstoque(local);
        recebimento.setAtor(ator);
        recebimento.setRecebidoEm(request.recebidoEm() != null
                ? request.recebidoEm() : OffsetDateTime.now());
        recebimento.setObservacao(normalizarOpcional(request.observacao()));

        for (ItemRecebimentoCompraRequest itemRequest : request.itens()) {
            ItemOrdemCompra itemOrdem = itensOrdem.get(itemRequest.itemOrdemCompraId());
            itemOrdem.receber(itemRequest.quantidade());
            Long movimentoId = estoque.registrarRecebimento(organizacaoId, atorId,
                    itemOrdem.getProduto().getId(), local.getId(), itemRequest.quantidade(),
                    itemOrdem.getCustoUnitarioFinal(), RECURSO_ORDEM,
                    ordem.getId() + ":" + itemOrdem.getSequencia() + ":" + chave,
                    "Recebimento da ordem " + ordem.getNumero());
            ItemRecebimentoCompra itemRecebimento = new ItemRecebimentoCompra();
            itemRecebimento.setItemOrdemCompra(itemOrdem);
            itemRecebimento.setProduto(itemOrdem.getProduto());
            itemRecebimento.setQuantidade(itemRequest.quantidade());
            itemRecebimento.setCustoUnitario(itemOrdem.getCustoUnitarioFinal());
            itemRecebimento.setMovimentoEstoqueId(movimentoId);
            recebimento.adicionarItem(itemRecebimento);
        }
        ordem.atualizarStatusRecebimento();
        recebimento = recebimentos.saveAndFlush(recebimento);
        ordem = ordens.saveAndFlush(ordem);
        idempotencia.registrar(organizacaoId, atorId, OPERACAO_RECEBER, chave,
                requestHash, RECURSO_RECEBIMENTO, recebimento.getId());
        Map<String, Object> payload = snapshotRecebimento(recebimento);
        auditar(ordem, "ORDEM_COMPRA_RECEBIDA", anteriorOrdem, snapshot(ordem), null);
        auditoria.registrar(new RegistrarAuditoriaCommand(organizacaoId,
                "RECEBIMENTO_COMPRA_REGISTRADO", RECURSO_RECEBIMENTO,
                recebimento.getId(), null, null, null, null, payload, null, null,
                ClasseRetencaoAuditoria.OPERACIONAL));
        publicar(ordem, "CompraRecebida", payload);
        return new RecebimentoRegistrado(ordem, recebimento);
    }

    private void preencher(OrdemCompra ordem, OrdemCompraRequest request, String numero) {
        Map<Long, ItemOrdemCompra> itensExistentes = new LinkedHashMap<>();
        ordem.getItens().forEach(item -> itensExistentes.put(item.getProduto().getId(), item));
        Fornecedor fornecedor = fornecedores
                .findByIdAndOrganizacaoId(request.fornecedorId(), ordem.getOrganizacaoId())
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Fornecedor ativo", request.fornecedorId()));
        LocalDate emissao = request.dataEmissao() != null
                ? request.dataEmissao() : LocalDate.now();
        if (emissao.isAfter(LocalDate.now())) {
            throw new ConflitoException("A data da ordem nao pode estar no futuro.");
        }
        if (request.previsaoEntrega() != null && request.previsaoEntrega().isBefore(emissao)) {
            throw new ConflitoException("A previsao de entrega nao pode anteceder a data da ordem.");
        }
        CondicaoPagamento condicaoPagamento = request.condicaoPagamentoId() == null
                ? fornecedor.getCondicaoPagamento()
                : condicoesPagamento.findByIdAndOrganizacaoId(
                        request.condicaoPagamentoId(), ordem.getOrganizacaoId())
                        .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "CondicaoPagamento ativa", request.condicaoPagamentoId()));
        if (condicaoPagamento != null && !Boolean.TRUE.equals(condicaoPagamento.getAtivo())) {
            condicaoPagamento = null;
        }
        Transportadora transportadora = request.transportadoraId() == null
                ? fornecedor.getTransportadora()
                : transportadoras.findByIdAndOrganizacaoId(
                        request.transportadoraId(), ordem.getOrganizacaoId())
                        .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Transportadora ativa", request.transportadoraId()));
        if (transportadora != null && !Boolean.TRUE.equals(transportadora.getAtivo())) {
            transportadora = null;
        }
        LocalEstoque localEntrega = request.localEstoqueEntregaId() == null ? null
                : locais.findAtivoParaMovimento(
                        request.localEstoqueEntregaId(), ordem.getOrganizacaoId())
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "LocalEstoque ativo", request.localEstoqueEntregaId()));
        BigDecimal descontoGeral = nz(request.valorDesconto());
        if (descontoGeral.signum() != 0) {
            throw new IllegalArgumentException(
                    "Desconto geral nao e permitido; informe o desconto em cada item.");
        }
        ordem.setNumero(numero);
        ordem.setFornecedor(fornecedor);
        ordem.setNumeroNota(normalizarIdentificadorFiscal(request.numeroNota()));
        ordem.setSerieNota(normalizarIdentificadorFiscal(request.serieNota()));
        ordem.setModeloNota(normalizarIdentificadorFiscal(request.modeloNota()));
        ordem.setDataEmissao(emissao);
        ordem.setPrevisaoEntrega(request.previsaoEntrega());
        ordem.setCondicaoPagamento(condicaoPagamento);
        ordem.setTransportadora(transportadora);
        ordem.setLocalEstoqueEntrega(localEntrega);
        ordem.setTipoFrete(request.tipoFrete() != null ? request.tipoFrete()
                : nz(request.valorFrete()).signum() > 0
                        ? TipoFreteOrdemCompra.CIF : TipoFreteOrdemCompra.SEM_FRETE);
        ordem.setReferenciaFornecedor(normalizarOpcional(request.referenciaFornecedor()));
        ordem.setMoeda(normalizarMoeda(request.moeda()));
        ordem.setValorFrete(nz(request.valorFrete()));
        ordem.setValorSeguro(nz(request.valorSeguro()));
        ordem.setOutrasDespesas(nz(request.outrasDespesas()));
        ordem.setValorDesconto(BigDecimal.ZERO);
        ordem.setObservacao(normalizarOpcional(request.observacao()));
        ordem.setObservacaoInterna(normalizarOpcional(request.observacaoInterna()));

        Set<Long> produtosInformados = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int sequencia = 1;
        for (ItemOrdemCompraRequest itemRequest : request.itens()) {
            if (!produtosInformados.add(itemRequest.produtoId())) {
                throw new IllegalArgumentException("Um produto nao pode ser repetido na ordem.");
            }
            Produto produto = produtos
                    .findByIdAndOrganizacaoId(itemRequest.produtoId(), ordem.getOrganizacaoId())
                    .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Produto ativo", itemRequest.produtoId()));
            BigDecimal desconto = nz(itemRequest.valorDesconto());
            BigDecimal total = itemRequest.quantidade().multiply(itemRequest.valorUnitario())
                    .subtract(desconto).setScale(2, RoundingMode.HALF_UP);
            if (total.signum() < 0) {
                throw new ConflitoException("O desconto do item nao pode exceder seu valor.");
            }
            ItemOrdemCompra item = itensExistentes.get(produto.getId());
            boolean novoItem = item == null;
            if (novoItem) {
                item = new ItemOrdemCompra();
                item.setQuantidadeRecebida(BigDecimal.ZERO);
            }
            item.setProduto(produto);
            item.setCodigoProdutoFornecedor(produtoFornecedores
                    .findByProdutoIdAndFornecedorIdAndOrganizacaoId(
                            produto.getId(), fornecedor.getId(), ordem.getOrganizacaoId())
                    .filter(vinculo -> Boolean.TRUE.equals(vinculo.getAtivo()))
                    .map(vinculo -> vinculo.getCodigoProd())
                    .orElse(null));
            item.setSequencia(sequencia++);
            item.setQuantidadePedida(itemRequest.quantidade());
            item.setValorUnitario(itemRequest.valorUnitario());
            item.setValorDesconto(desconto);
            item.setValorTotal(total);
            if (novoItem) {
                ordem.adicionarItem(item);
            }
            subtotal = subtotal.add(total);
        }
        ordem.getItens().removeIf(
                item -> !produtosInformados.contains(item.getProduto().getId()));
        ordem.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        ratear(ordem.getItens(), ordem.getSubtotal(), ordem.getValorFrete(),
                ItemOrdemCompra::setRateioFrete);
        ratear(ordem.getItens(), ordem.getSubtotal(), ordem.getValorSeguro(),
                ItemOrdemCompra::setRateioSeguro);
        ratear(ordem.getItens(), ordem.getSubtotal(), ordem.getOutrasDespesas(),
                ItemOrdemCompra::setRateioOutrasDespesas);
        for (ItemOrdemCompra item : ordem.getItens()) {
            BigDecimal custoTotal = item.getValorTotal().add(item.getRateioFrete())
                    .add(item.getRateioSeguro()).add(item.getRateioOutrasDespesas())
                    .setScale(2, RoundingMode.HALF_UP);
            item.setCustoTotal(custoTotal);
            item.setCustoUnitarioFinal(custoTotal.divide(
                    item.getQuantidadePedida(), 2, RoundingMode.HALF_UP));
        }
        BigDecimal total = ordem.getSubtotal().add(ordem.getValorFrete())
                .add(ordem.getValorSeguro()).add(ordem.getOutrasDespesas())
                .setScale(2, RoundingMode.HALF_UP);
        ordem.setValorTotal(total);
    }

    private static void ratear(List<ItemOrdemCompra> itens, BigDecimal baseValor,
            BigDecimal despesa, BiConsumer<ItemOrdemCompra, BigDecimal> atribuir) {
        if (itens.isEmpty() || despesa.signum() == 0) {
            itens.forEach(item -> atribuir.accept(item, BigDecimal.ZERO));
            return;
        }
        boolean porValor = baseValor.signum() > 0;
        BigDecimal base = porValor ? baseValor : itens.stream()
                .map(ItemOrdemCompra::getQuantidadePedida)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int indice = 0; indice < itens.size(); indice++) {
            ItemOrdemCompra item = itens.get(indice);
            BigDecimal parcela;
            if (indice == itens.size() - 1) {
                parcela = despesa.subtract(acumulado).setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal peso = porValor ? item.getValorTotal() : item.getQuantidadePedida();
                parcela = despesa.multiply(peso).divide(base, 2, RoundingMode.HALF_UP);
                acumulado = acumulado.add(parcela);
            }
            atribuir.accept(item, parcela);
        }
    }

    private OrdemCompra bloquear(Long organizacaoId, Long id) {
        return ordens.findParaAtualizar(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OrdemCompra", id));
    }

    private static void exigirVersao(OrdemCompra ordem, long esperada) {
        if (ordem.getVersion() == null || ordem.getVersion() != esperada) {
            throw new VersaoOrdemCompraDivergenteException();
        }
    }

    private void auditar(OrdemCompra ordem, String acao, Map<String, Object> anterior,
            Map<String, Object> posterior, String motivo) {
        auditoria.registrar(new RegistrarAuditoriaCommand(ordem.getOrganizacaoId(), acao,
                RECURSO_ORDEM, ordem.getId(), null, null, motivo, anterior, posterior,
                null, ordem.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
    }

    private void publicar(OrdemCompra ordem, String evento, Map<String, Object> payload) {
        outbox.publicar(new PublicarEventoOutboxCommand(evento, ordem.getOrganizacaoId(),
                RECURSO_ORDEM, ordem.getId(), ordem.getVersion(), null, 1, payload));
    }

    private static Map<String, Object> snapshot(OrdemCompra ordem) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ordemCompraId", ordem.getId());
        snapshot.put("numero", ordem.getNumero());
        snapshot.put("numeroNota", ordem.getNumeroNota());
        snapshot.put("serieNota", ordem.getSerieNota());
        snapshot.put("modeloNota", ordem.getModeloNota());
        snapshot.put("fornecedorId", ordem.getFornecedor().getId());
        snapshot.put("condicaoPagamentoId", ordem.getCondicaoPagamento() == null
                ? null : ordem.getCondicaoPagamento().getId());
        snapshot.put("transportadoraId", ordem.getTransportadora() == null
                ? null : ordem.getTransportadora().getId());
        snapshot.put("localEstoqueEntregaId", ordem.getLocalEstoqueEntrega() == null
                ? null : ordem.getLocalEstoqueEntrega().getId());
        snapshot.put("compradorId", ordem.getComprador() == null
                ? null : ordem.getComprador().getId());
        snapshot.put("tipoFrete", ordem.getTipoFrete().name());
        snapshot.put("status", ordem.getStatus().name());
        snapshot.put("valorTotal", ordem.getValorTotal());
        snapshot.put("quantidadeItens", ordem.getItens().size());
        return snapshot;
    }

    private static Map<String, Object> snapshotRecebimento(RecebimentoCompra recebimento) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recebimentoCompraId", recebimento.getId());
        snapshot.put("ordemCompraId", recebimento.getOrdemCompra().getId());
        snapshot.put("localEstoqueId", recebimento.getLocalEstoque().getId());
        snapshot.put("atorId", recebimento.getAtor().getId());
        snapshot.put("recebidoEm", recebimento.getRecebidoEm().toString());
        snapshot.put("quantidadeItens", recebimento.getItens().size());
        return snapshot;
    }

    private static String gerarNumeroInterno() {
        return "OC-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalizarIdentificadorFiscal(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    private void exigirChaveFiscalDisponivel(Long organizacaoId, Long fornecedorId,
            String numeroNota, String serieNota, String modeloNota, Long ignorarId) {
        boolean existente = ignorarId == null
                ? ordens.existsByOrganizacaoIdAndFornecedorIdAndNumeroNotaIgnoreCaseAndSerieNotaIgnoreCaseAndModeloNotaIgnoreCase(
                        organizacaoId, fornecedorId, numeroNota, serieNota, modeloNota)
                : ordens.existsByOrganizacaoIdAndFornecedorIdAndNumeroNotaIgnoreCaseAndSerieNotaIgnoreCaseAndModeloNotaIgnoreCaseAndIdNot(
                        organizacaoId, fornecedorId, numeroNota, serieNota, modeloNota,
                        ignorarId);
        if (existente) {
            throw new ConflitoException(
                    "Ja existe uma compra para este fornecedor, numero, serie e modelo de nota.");
        }
    }

    private static String normalizarMoeda(String moeda) {
        return moeda == null || moeda.isBlank() ? "BRL" : moeda.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
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

    private static void conferirHash(String anterior, String atual) {
        if (!atual.equals(anterior)) {
            throw new ConflitoException("A chave de idempotencia ja foi usada com outro payload.");
        }
    }

    private static String hashRequest(OrdemCompraRequest request) {
        StringBuilder valor = new StringBuilder()
                .append(request.fornecedorId()).append('|').append(request.numeroNota()).append('|')
                .append(request.serieNota()).append('|').append(request.modeloNota()).append('|')
                .append(request.dataEmissao()).append('|').append(request.previsaoEntrega())
                .append('|').append(request.condicaoPagamentoId())
                .append('|').append(request.transportadoraId())
                .append('|').append(request.localEstoqueEntregaId())
                .append('|').append(request.tipoFrete())
                .append('|').append(request.referenciaFornecedor())
                .append('|').append(request.moeda()).append('|').append(request.valorFrete())
                .append('|').append(request.valorSeguro()).append('|')
                .append(request.outrasDespesas())
                .append('|').append(request.valorDesconto()).append('|').append(request.observacao())
                .append('|').append(request.observacaoInterna());
        request.itens().forEach(item -> valor.append('|').append(item.produtoId())
                .append(':').append(item.quantidade()).append(':').append(item.valorUnitario())
                .append(':').append(item.valorDesconto()));
        return sha256(valor.toString());
    }

    private static String hashRecebimento(Long ordemId, ReceberOrdemCompraRequest request) {
        StringBuilder valor = new StringBuilder().append(ordemId).append('|')
                .append(request.localEstoqueId()).append('|').append(request.recebidoEm())
                .append('|').append(request.observacao());
        request.itens().forEach(item -> valor.append('|').append(item.itemOrdemCompraId())
                .append(':').append(item.quantidade()));
        return sha256(valor.toString());
    }

    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    public record RecebimentoRegistrado(
            OrdemCompra ordem, RecebimentoCompra recebimento) {
    }
}
