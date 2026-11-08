package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.CustoMedioPonderado;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaEntradaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.PreviaNotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.CustoMedioItemNotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.ParcelaNotaEntradaResponse;
import com.kaneko.estacionamento.conveniencia.ProdutoFornecedorRepository;
import com.kaneko.estacionamento.logistica.TransportadoraRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Locale;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaRequest;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio da nota fiscal de entrada (compra). Coordena o calculo dos
 * totais e rateios na criacao e, na confirmacao, os efeitos colaterais: soma do
 * estoque dos produtos e geracao dos titulos a pagar conforme a condicao de
 * pagamento.
 */
@Service
public class NotaEntradaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final NotaEntradaRepository repository;
    private final FornecedorRepository fornecedorRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final ProdutoRepository produtoRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final EstoqueService estoqueService;
    private final AuditoriaService auditoria;
    private final IdempotenciaRepository idempotencia;

    private final ProdutoFornecedorRepository produtoFornecedores;
    private final TransportadoraRepository transportadoras;
    private final JdbcTemplate jdbc;

    public NotaEntradaService(NotaEntradaRepository repository,
                              FornecedorRepository fornecedorRepository,
                              CondicaoPagamentoRepository condicaoPagamentoRepository,
                              ProdutoRepository produtoRepository,
                              ContaPagarRepository contaPagarRepository,
                              LocalEstoqueRepository localEstoqueRepository,
                              EstoqueService estoqueService,
                              AuditoriaService auditoria, IdempotenciaRepository idempotencia,
                              ProdutoFornecedorRepository produtoFornecedores,
                              TransportadoraRepository transportadoras, JdbcTemplate jdbc) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.produtoRepository = produtoRepository;
        this.contaPagarRepository = contaPagarRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.estoqueService = estoqueService;
        this.auditoria = auditoria;
        this.idempotencia = idempotencia;
        this.produtoFornecedores = produtoFornecedores;
        this.transportadoras = transportadoras;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<NotaEntrada> listar(Long organizacaoId, Pageable pageable) {
        return listar(organizacaoId, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NotaEntrada> listar(Long organizacaoId, String numero, SituacaoNota situacao,
            Long fornecedorId, Pageable pageable) {
        Page<Long> ids = repository.filtrarIds(organizacaoId,
                numero == null ? "" : numero.trim(), situacao, fornecedorId, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<Long, NotaEntrada> notasPorId = repository.findAllByIdIn(ids.getContent())
                .stream().collect(java.util.stream.Collectors.toMap(NotaEntrada::getId, nota -> nota));
        List<NotaEntrada> notasOrdenadas = ids.getContent().stream()
                .map(notasPorId::get)
                .toList();
        return new PageImpl<>(notasOrdenadas, pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotaEntrada buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
    }

    @Transactional
    public NotaEntrada criarIdempotente(Long organizacaoId, Long atorId, String chave, NotaEntradaRequest req) {
        if (organizacaoId == null || atorId == null) {
            throw new IllegalArgumentException("O registro idempotente exige organização ativa.");
        }
        if (chave.isBlank() || chave.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key deve possuir entre 1 e 128 caracteres.");
        }
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(req.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
        idempotencia.bloquear(organizacaoId, atorId, "CRIAR_NOTA_ENTRADA", chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, "CRIAR_NOTA_ENTRADA", chave);
        if (anterior.isPresent()) {
            if (!hash.equals(anterior.get().requestHash())) {
                throw new ConflitoException("A chave já foi usada para outra nota. Recupere a nota anterior antes de criar uma nova.");
            }
            return buscar(organizacaoId, anterior.get().recursoId());
        }
        NotaEntrada nota = criar(organizacaoId, req);
        idempotencia.registrar(organizacaoId, atorId, "CRIAR_NOTA_ENTRADA", chave,
                hash, "NOTA_ENTRADA", nota.getId());
        return nota;
    }

    @Transactional
    public NotaEntrada criar(Long organizacaoId, NotaEntradaRequest req) {
        NotaEntrada nota = preparar(organizacaoId, req, null);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CRIADA", null, snapshot(nota));
        return nota;
    }

    @Transactional
    public PreviaNotaEntradaResponse previa(Long org, NotaEntradaRequest req) {
        return resumo(preparar(org, req, null));
    }

    @Transactional
    public PreviaNotaEntradaResponse previa(Long org, Long id, long versao, NotaEntradaRequest req) {
        NotaEntrada atual = bloquear(org, id);
        NotaEntradaHttpVersion.exigir(atual, versao);
        NotaEntradaHttpVersion.pendente(atual);
        exigirCronogramaSalvo(atual);
        NotaEntrada proposta = preparar(org, req, id);
        if (!assinatura(atual).equals(assinatura(proposta)))
            throw new ConflitoException("Salve as alteracoes do rascunho e confira a nova revisao antes de confirmar.");
        return resumo(atual);
    }

    @Transactional
    public NotaEntrada prepararConferencia(Long org, NotaEntradaRequest req) {
        return preparar(org, req, null, 2);
    }

    private static List<Object> assinatura(NotaEntrada n) {
        return java.util.Arrays.asList(n.getNumero(), n.getModelo(), n.getSerie(), n.getFornecedor().getId(),
                n.getCondicaoPagamento() == null ? null : n.getCondicaoPagamento().getId(),
                n.getTransportadora() == null ? null : n.getTransportadora().getId(),
                n.getLocalEstoque() == null ? null : n.getLocalEstoque().getId(), n.getDataEmissao(), n.getDataChegada(),
                n.getTipoFrete(), n.getValorFrete().stripTrailingZeros(), n.getValorSeguro().stripTrailingZeros(),
                n.getOutrasDespesas().stripTrailingZeros(), n.getValorDesconto().stripTrailingZeros(),
                texto(n.getObservacao()), texto(n.getObservacaoInterna()),
                n.getItens().stream().map(i -> java.util.Arrays.asList(i.getProduto().getId(),
                        i.getQuantidade().stripTrailingZeros(), i.getValorUnitario().stripTrailingZeros(),
                        i.getValorDesconto().stripTrailingZeros())).toList());
    }

    private static String texto(String s) { return s == null ? "" : s.trim(); }

    private static void exigirCronogramaSalvo(NotaEntrada nota) {
        if (!nota.isCronogramaDefinido())
            throw new ConflitoException("Este rascunho anterior ao fluxo atual ainda nao possui parcelas congeladas. "
                    + "Salve o rascunho, mesmo sem alterar os campos, antes de conferir e confirmar.");
    }

    @Transactional
    public PreviaNotaEntradaResponse previaComCronograma(Long org, NotaEntradaRequest req, List<ParcelaNotaEntrada> parcelas) {
        NotaEntrada nota = preparar(org, req, null, 2);
        aplicarCronograma(nota, parcelas);
        return resumo(nota);
    }

    @Transactional
    public NotaEntrada criarComCronograma(Long org, NotaEntradaRequest req, List<ParcelaNotaEntrada> parcelas) {
        NotaEntrada nota = preparar(org, req, null, 2);
        aplicarCronograma(nota, parcelas);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CRIADA", null, snapshot(nota));
        return nota;
    }

    public static List<ParcelaNotaEntrada> copiarCronograma(List<ParcelaNotaEntrada> parcelas) {
        return parcelas.stream().map(p -> {
            var copia = new ParcelaNotaEntrada(); copia.setNumeroParcela(p.getNumeroParcela());
            copia.setValor(p.getValor()); copia.setDataVencimento(p.getDataVencimento());
            copia.setFormaPagamento(p.getFormaPagamento()); copia.setFormaPagamentoNome(p.getFormaPagamentoNome());
            return copia;
        }).toList();
    }

    private static void aplicarCronograma(NotaEntrada nota, List<ParcelaNotaEntrada> parcelas) {
        BigDecimal soma = parcelas.stream().map(ParcelaNotaEntrada::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.compareTo(nota.getValorTotal()) != 0 || parcelas.stream().anyMatch(p -> p.getValor().signum()<=0
                || p.getDataVencimento().isBefore(nota.getDataEmissao())))
            throw new ConflitoException("A previsao congelada nao corresponde ao documento. Refaca a conferencia.");
        nota.getParcelas().clear(); nota.getParcelas().addAll(copiarCronograma(parcelas));
        nota.setCronogramaDefinido(true);
    }

    @Transactional(readOnly = true)
    public PreviaNotaEntradaResponse previaPersistida(Long org, Long id) {
        return resumo(buscar(org, id));
    }

    public PreviaNotaEntradaResponse resumo(NotaEntrada nota) {
        var parcelas = cronograma(nota);
        var valores = new ArrayList<ParcelaNotaEntradaResponse>();
        for (var p : parcelas) valores.add(new ParcelaNotaEntradaResponse(p.getNumeroParcela(),
                parcelas.size(), p.getValor(), p.getDataVencimento(),
                p.getFormaPagamento() == null ? null : p.getFormaPagamento().getId(), p.getFormaPagamentoNome()));
        BigDecimal custo = nota.getItens().stream().map(i -> i.getCustoTotal() == null
                ? i.getValorTotal().add(i.getRateioFrete()).add(i.getRateioSeguro()).add(i.getRateioOutras())
                : i.getCustoTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PreviaNotaEntradaResponse(NotaEntradaResponse.from(nota), valores, custo,
                nota.getValorTotal(), nota.getValorDesconto(), calcularCustosMedios(nota));
    }

    private List<CustoMedioItemNotaEntradaResponse> calcularCustosMedios(NotaEntrada nota) {
        Map<Long, BigDecimal> saldos = new HashMap<>();
        Map<Long, BigDecimal> custos = new HashMap<>();
        List<CustoMedioItemNotaEntradaResponse> resultado = new ArrayList<>();
        for (ItemNotaEntrada item : nota.getItens()) {
            boolean registrado = item.getSaldoEstoqueAnterior() != null
                    && item.getCustoMedioAnterior() != null
                    && item.getCustoMedioPosterior() != null;
            if (registrado) {
                resultado.add(custoMedioItem(item, item.getSaldoEstoqueAnterior(),
                        item.getCustoMedioAnterior(), item.getCustoMedioPosterior(), true, false));
                continue;
            }
            boolean projetar = nota.getSituacao() == SituacaoNota.PENDENTE;
            if (!projetar) {
                resultado.add(custoMedioItem(item, null, null, null, false, false));
                continue;
            }
            Long produtoId = item.getProduto().getId();
            BigDecimal saldoAnterior = saldos.computeIfAbsent(produtoId,
                    ignored -> saldoTotalAtual(nota.getOrganizacaoId(), item.getProduto()));
            BigDecimal custoAnterior = custos.computeIfAbsent(produtoId,
                    ignored -> item.getProduto().getCusto() == null
                            ? BigDecimal.ZERO : item.getProduto().getCusto());
            BigDecimal custoPosterior = CustoMedioPonderado.calcularEmMilionesimos(saldoAnterior,
                    custoAnterior, item.getQuantidade(), item.getCustoPrecoFinal());
            resultado.add(custoMedioItem(item, saldoAnterior, custoAnterior,
                    custoPosterior, true, true));
            saldos.put(produtoId, saldoAnterior.add(item.getQuantidade()));
            custos.put(produtoId, custoPosterior);
        }
        return resultado;
    }

    private BigDecimal saldoTotalAtual(Long organizacaoId, Produto produto) {
        if (organizacaoId == null) {
            return produto.getQuantidade() == null ? BigDecimal.ZERO : produto.getQuantidade();
        }
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(quantidade_micros),0) * 0.000001 FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=?
                """, BigDecimal.class, organizacaoId, produto.getId());
    }

    private static CustoMedioItemNotaEntradaResponse custoMedioItem(ItemNotaEntrada item,
            BigDecimal saldoAnterior, BigDecimal custoAnterior, BigDecimal custoPosterior,
            boolean aplicaAoEstoque, boolean projecao) {
        return new CustoMedioItemNotaEntradaResponse(item.getSequencia(),
                item.getProduto().getId(), saldoAnterior, custoAnterior,
                item.getQuantidade(), item.getCustoPrecoFinal(), custoPosterior,
                aplicaAoEstoque, projecao);
    }

    @Transactional
    public NotaEntrada atualizar(Long org, Long id, long versao, NotaEntradaRequest req) {
        NotaEntrada atual = bloquear(org, id);
        NotaEntradaHttpVersion.exigir(atual, versao);
        NotaEntradaHttpVersion.pendente(atual);
        NotaEntrada nova = preparar(org, req, id);
        Map<String, Object> anterior = snapshot(atual);
        atual.setNumero(nova.getNumero()); atual.setModelo(nova.getModelo()); atual.setSerie(nova.getSerie());
        atual.setFornecedor(nova.getFornecedor()); atual.setCondicaoPagamento(nova.getCondicaoPagamento());
        atual.setLocalEstoque(nova.getLocalEstoque());
        atual.setTransportadora(nova.getTransportadora()); atual.setObservacaoInterna(nova.getObservacaoInterna());
        atual.setDataEmissao(nova.getDataEmissao()); atual.setDataChegada(nova.getDataChegada());
        atual.setTipoFrete(nova.getTipoFrete()); atual.setValorProdutos(nova.getValorProdutos());
        atual.setValorFrete(nova.getValorFrete()); atual.setValorSeguro(nova.getValorSeguro());
        atual.setOutrasDespesas(nova.getOutrasDespesas()); atual.setValorDesconto(nova.getValorDesconto());
        atual.setValorTotal(nova.getValorTotal()); atual.setObservacao(nova.getObservacao());
        // Recriar linhas em dois passos evita conflito da sequencia unica durante orphanRemoval.
        atual.getItens().clear(); repository.flush();
        nova.getItens().forEach(atual::addItem);
        atual.getParcelas().clear(); atual.getParcelas().addAll(nova.getParcelas());
        atual.setCronogramaDefinido(true);
        atual.setUpdatedAt(java.time.OffsetDateTime.now());
        atual = repository.saveAndFlush(atual);
        auditar(atual, "NOTA_ENTRADA_ATUALIZADA", anterior, snapshot(atual));
        return atual;
    }

    public static void validarDataEmissao(LocalDate emissao, LocalDate hoje) {
        if (emissao.isAfter(hoje)) {
            throw new ConflitoException("A data de emissão não pode ser futura.");
        }
    }

    private NotaEntrada preparar(Long org, NotaEntradaRequest req, Long ignorarId) {
        return preparar(org, req, ignorarId, 6);
    }

    private NotaEntrada preparar(Long org, NotaEntradaRequest req, Long ignorarId, int escalaValorBruto) {
        if (req.itens() == null || req.itens().isEmpty()) throw new IllegalArgumentException("Informe os itens da nota.");
        Fornecedor fornecedor = (org == null ? fornecedorRepository.findByIdAndOrganizacaoIdIsNull(req.fornecedorId())
                : fornecedorRepository.findByIdAndOrganizacaoId(req.fornecedorId(), org))
                .filter(f -> !Boolean.FALSE.equals(f.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor ativo", req.fornecedorId()));
        NotaEntrada nota = new NotaEntrada(); nota.setOrganizacaoId(org);
        nota.setNumero(normalizar(req.numero()));
        nota.setModelo(normalizar(req.modelo() == null || req.modelo().isBlank() ? "55" : req.modelo()));
        nota.setSerie(normalizar(req.serie() == null || req.serie().isBlank() ? "1" : req.serie()));
        nota.setFornecedor(fornecedor);
        if (repository.existeIdentidade(org, fornecedor.getId(), nota.getNumero(), nota.getSerie(), nota.getModelo(), ignorarId))
            throw new ConflitoException("J? existe uma nota com este numero/serie/modelo para este fornecedor.");
        var condicao = req.condicaoPagamentoId() == null ? fornecedor.getCondicaoPagamento()
                : buscarCondicao(org, req.condicaoPagamentoId());
        if (condicao != null && !Boolean.TRUE.equals(condicao.getAtivo())) {
            if (req.condicaoPagamentoId() != null) throw new ConflitoException("A condicao de pagamento esta inativa.");
            condicao = null;
        }
        nota.setCondicaoPagamento(condicao);
        var transportadora = req.transportadoraId() == null ? fornecedor.getTransportadora()
                : (org == null ? transportadoras.findByIdAndOrganizacaoIdIsNull(req.transportadoraId())
                        : transportadoras.findByIdAndOrganizacaoId(req.transportadoraId(), org))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Transportadora", req.transportadoraId()));
        if (transportadora != null && !Boolean.TRUE.equals(transportadora.getAtivo())) {
            if (req.transportadoraId() != null) throw new ConflitoException("A transportadora esta inativa.");
            transportadora = null;
        }
        nota.setTransportadora(transportadora); nota.setObservacaoInterna(req.observacaoInterna());
        if (org != null) {
            if (req.localEstoqueId() == null) throw new IllegalArgumentException("O local de estoque e obrigatorio.");
            nota.setLocalEstoque(localEstoqueRepository.findByIdAndOrganizacaoId(req.localEstoqueId(), org)
                    .filter(l -> Boolean.TRUE.equals(l.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("LocalEstoque ativo", req.localEstoqueId())));
        }
        LocalDate hoje = LocalDate.now();
        LocalDate emissao = req.dataEmissao() == null ? hoje : req.dataEmissao();
        validarDataEmissao(emissao, hoje);
        if (req.dataChegada() != null && req.dataChegada().isBefore(emissao))
            throw new ConflitoException("A chegada não pode ser anterior à emissão.");
        nota.setDataEmissao(emissao); nota.setDataChegada(req.dataChegada());
        nota.setTipoFrete(req.tipoFrete() == null ? TipoFrete.CIF : req.tipoFrete());
        nota.setValorFrete(exato(nz(req.valorFrete()), 19));
        nota.setValorSeguro(exato(nz(req.valorSeguro()), 19));
        nota.setOutrasDespesas(exato(nz(req.outrasDespesas()), 19));
        nota.setValorDesconto(exato(nz(req.valorDesconto()), 19));
        nota.setObservacao(req.observacao());
        BigDecimal valorProdutos = BigDecimal.ZERO;
        int seq = 1;
        for (ItemNotaEntradaRequest ir : req.itens()) {
            Produto produto = (org == null ? produtoRepository.findByIdAndOrganizacaoIdIsNull(ir.produtoId())
                    : produtoRepository.findByIdAndOrganizacaoId(ir.produtoId(), org))
                    .filter(p -> !Boolean.FALSE.equals(p.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto ativo", ir.produtoId()));
            ItemNotaEntrada item = new ItemNotaEntrada(); item.setOrganizacaoId(org); item.setProduto(produto);
            item.setSequencia(seq++); item.setQuantidade(exato(ir.quantidade(), 18));
            if (item.getQuantidade().signum() <= 0) throw new ConflitoException("A quantidade deve ser positiva.");
            item.setValorUnitario(exato(ir.valorUnitario(), 19));
            // O XML já fixa o bruto fiscal em centavos, validado na conciliação.
            BigDecimal bruto = item.getQuantidade().multiply(item.getValorUnitario())
                    .setScale(escalaValorBruto, RoundingMode.HALF_UP).setScale(6);
            BigDecimal percentual = exato(nz(ir.percentualDesconto()), 9);
            if (percentual.compareTo(CEM) > 0) throw new ConflitoException("O percentual nao pode exceder 100.");
            BigDecimal porPercentual = bruto.multiply(percentual).divide(CEM, 6, RoundingMode.HALF_UP);
            BigDecimal desconto = ir.valorDesconto() == null ? porPercentual : exato(ir.valorDesconto(), 19);
            if (ir.percentualDesconto() != null && ir.valorDesconto() != null && desconto.compareTo(porPercentual) != 0)
                throw new ConflitoException("O valor e o percentual de desconto do item sao contraditorios.");
            if (desconto.compareTo(bruto) > 0) throw new ConflitoException("O desconto do item nao pode exceder o seu valor.");
            item.setValorDesconto(desconto);
            item.setPercentualDesconto(ir.percentualDesconto() != null ? percentual : bruto.signum() == 0
                    ? BigDecimal.ZERO : desconto.multiply(CEM).divide(bruto, 6, RoundingMode.HALF_UP));
            item.setValorTotal(bruto.subtract(desconto));
            item.setUnidadeMedida(produto.getUnidadeMedida() == null ? null : produto.getUnidadeMedida().getSigla());
            item.setCodigoProdutoFornecedor(produtoFornecedores.findByProdutoIdAndFornecedorIdAndOrganizacaoId(
                    produto.getId(), fornecedor.getId(), org).filter(v -> Boolean.TRUE.equals(v.getAtivo()))
                    .map(v -> v.getCodigoProd()).orElse(null));
            nota.addItem(item); valorProdutos = valorProdutos.add(item.getValorTotal());
        }
        nota.setValorProdutos(exato(valorProdutos, 19));
        BigDecimal total = valorProdutos.add(nota.getValorFrete()).add(nota.getValorSeguro())
                .add(nota.getOutrasDespesas()).subtract(nota.getValorDesconto());
        if (total.signum() < 0) throw new ConflitoException("O desconto da nota nao pode exceder o seu valor.");
        nota.setValorTotal(exato(total, 19));
        ratearDespesas(nota.getItens(), valorProdutos, nota.getValorFrete(), nota.getValorSeguro(), nota.getOutrasDespesas());
        nota.getParcelas().addAll(calcularParcelas(nota)); nota.setCronogramaDefinido(true);
        return nota;
    }

    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("O identificador da nota e obrigatorio.");
        String texto = valor.trim().toUpperCase(Locale.ROOT);
        return texto.matches("[0-9]+") ? texto.replaceFirst("^0+(?!$)", "") : texto;
    }

    private static BigDecimal exato(BigDecimal valor, int precisao) {
        try {
            BigDecimal exato = Objects.requireNonNull(valor).setScale(6, RoundingMode.UNNECESSARY);
            if (exato.signum() < 0 || exato.precision() > precisao) throw new ArithmeticException();
            return exato;
        } catch (ArithmeticException | NullPointerException ex) {
            throw new ConflitoException("Valor inválido: informe no máximo seis casas decimais e respeite o limite do campo.");
        }
    }

    private NotaEntrada bloquear(Long org, Long id) {
        return org == null ? buscar(null, id) : repository.findParaAtualizar(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
    }

    /**
     * Confirma a nota: efetiva o estoque (soma a quantidade comprada e atualiza o
     * custo de cada produto) e gera os titulos a pagar.
     */
    @Transactional
    public NotaEntrada confirmar(Long organizacaoId, Long atorId, Long id) {
        return confirmar(organizacaoId, atorId, id, null);
    }

    @Transactional
    public NotaEntrada confirmar(Long organizacaoId, Long atorId, Long id, Long versao) {
        NotaEntrada nota = bloquear(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNota.PENDENTE)
            throw new ConflitoException("Apenas notas pendentes podem ser confirmadas. Consulte o documento existente.");
        if (versao != null) NotaEntradaHttpVersion.exigir(nota, versao);
        validarDataEmissao(nota.getDataEmissao(), LocalDate.now());
        exigirCronogramaSalvo(nota);
        // Mesmo ordenamento em todos os documentos; locks duram ate o commit.
        if (organizacaoId != null) estoqueService.bloquearProdutos(organizacaoId,
                nota.getItens().stream().map(i -> i.getProduto().getId()).toList());
        for (ItemNotaEntrada item : nota.getItens()) {
                Produto produto = item.getProduto();
                if (organizacaoId == null) {
                    BigDecimal saldoAnterior = produto.getQuantidade();
                    BigDecimal custoAnterior = produto.getCusto() == null
                            ? BigDecimal.ZERO : produto.getCusto();
                    BigDecimal custoPosterior = CustoMedioPonderado.calcularEmMilionesimos(saldoAnterior,
                            custoAnterior, item.getQuantidade(), item.getCustoPrecoFinal());
                    produto.setQuantidade(saldoAnterior.add(item.getQuantidade()));
                    produto.setCusto(custoPosterior);
                    item.setSaldoEstoqueAnterior(saldoAnterior);
                    item.setCustoMedioAnterior(custoAnterior);
                    item.setCustoMedioPosterior(custoPosterior);
                } else {
                    var recebimento = estoqueService.registrarRecebimentoComCustoMedio(
                            organizacaoId, atorId, produto.getId(), nota.getLocalEstoque().getId(),
                            item.getQuantidade(), item.getCustoPrecoFinal(), "NOTA_ENTRADA",
                            nota.getId() + ":" + item.getSequencia(),
                            "Confirmacao da nota de entrada " + nota.getNumero());
                    item.setSaldoEstoqueAnterior(recebimento.saldoTotalAnterior());
                    item.setCustoMedioAnterior(recebimento.custoMedioAnterior());
                    item.setCustoMedioPosterior(recebimento.custoMedioPosterior());
                }
                produto.setValorCompra(item.getValorUnitario());
                produto.recalcularLucro();
                produtoRepository.saveAndFlush(produto);
        }
        gerarTitulos(nota);
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CONFIRMADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaEntrada cancelar(Long organizacaoId, Long id) {
        return cancelar(organizacaoId, id, null);
    }

    @Transactional
    public NotaEntrada cancelar(Long organizacaoId, Long id, Long versao) {
        NotaEntrada nota = organizacaoId == null ? buscar(null, id)
                : repository.findParaAtualizar(id, organizacaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
        if (versao != null) NotaEntradaHttpVersion.exigir(nota, versao);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser cancelada.");
        }
        if (nota.getRevertidaEm() != null) {
            throw new ConflitoException("Uma nota revertida deve permanecer no histórico.");
        }
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CANCELADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CANCELADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        excluir(organizacaoId, id, null);
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id, Long versao) {
        NotaEntrada nota = organizacaoId == null ? buscar(null, id)
                : repository.findParaAtualizar(id, organizacaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
        if (versao != null) NotaEntradaHttpVersion.exigir(nota, versao);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser excluída.");
        }
        if (nota.getRevertidaEm() != null) {
            throw new ConflitoException("Uma nota revertida não pode ser excluída.");
        }
        Map<String, Object> anterior = snapshot(nota);
        repository.delete(nota);
        repository.flush();
        auditar(nota, "NOTA_ENTRADA_EXCLUIDA", anterior, null);
    }

    /** Distribui frete/seguro/outras despesas proporcionalmente ao valor de cada item. */
    private void ratearDespesas(List<ItemNotaEntrada> itens, BigDecimal base,
                                BigDecimal frete, BigDecimal seguro, BigDecimal outras) {
        List<BigDecimal> pesos = itens.stream().map(i -> base.signum() > 0 ? i.getValorTotal() : i.getQuantidade()).toList();
        var fretes = RateioMonetario.distribuirEmMilionesimos(frete, pesos);
        var seguros = RateioMonetario.distribuirEmMilionesimos(seguro, pesos);
        var despesas = RateioMonetario.distribuirEmMilionesimos(outras, pesos);
        for (int i = 0; i < itens.size(); i++) {
            ItemNotaEntrada item = itens.get(i);
            item.setRateioFrete(fretes.get(i)); item.setRateioSeguro(seguros.get(i)); item.setRateioOutras(despesas.get(i));
            BigDecimal custo = item.getValorTotal().add(fretes.get(i)).add(seguros.get(i)).add(despesas.get(i));
            item.setCustoTotal(exato(custo, 19));
            item.setCustoPrecoFinal(exato(custo.divide(item.getQuantidade(), 6, RoundingMode.HALF_UP), 19));
        }
    }

    private List<ParcelaNotaEntrada> cronograma(NotaEntrada nota) {
        if (!nota.isCronogramaDefinido() && nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            return contaPagarRepository.findByNotaEntradaIdAndOrganizacaoIdOrderByNumeroParcelaAsc(nota.getId(), nota.getOrganizacaoId())
                    .stream().filter(c -> c.getValorOriginal().signum()>0).map(c -> {
                        var p = new ParcelaNotaEntrada(); p.setNumeroParcela(c.getNumeroParcela());
                        p.setValor(c.getValorOriginal()); p.setDataVencimento(c.getDataVencimento());
                        p.setFormaPagamento(c.getFormaPagamento());
                        p.setFormaPagamentoNome(c.getFormaPagamento() == null ? null : c.getFormaPagamento().getNome());
                        return p;
                    }).toList();
        }
        return nota.isCronogramaDefinido() ? nota.getParcelas() : calcularParcelas(nota);
    }

    private List<ParcelaNotaEntrada> calcularParcelas(NotaEntrada nota) {
        if (nota.getValorTotal().signum() == 0) return List.of();
        List<ParcelaCondicaoPagamento> condicoes = nota.getCondicaoPagamento() == null ? List.of()
                : nota.getCondicaoPagamento().getParcelas().stream()
                    .sorted(Comparator.comparing(ParcelaCondicaoPagamento::getNumero)).toList();
        if (!condicoes.isEmpty() && condicoes.stream().map(ParcelaCondicaoPagamento::getPercentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(CEM) != 0)
            throw new ConflitoException("A soma das parcelas da condicao deve ser 100%.");
        List<BigDecimal> valores = RateioMonetario.distribuirEmMilionesimos(nota.getValorTotal(), condicoes.isEmpty()
                ? List.of(BigDecimal.ONE) : condicoes.stream().map(ParcelaCondicaoPagamento::getPercentual).toList());
        var resultado = new ArrayList<ParcelaNotaEntrada>();
        for (int i = 0; i < valores.size(); i++) {
            if (valores.get(i).signum() == 0) continue;
            var origem = condicoes.isEmpty() ? null : condicoes.get(i);
            var parcela = new ParcelaNotaEntrada(); parcela.setNumeroParcela(resultado.size() + 1);
            parcela.setValor(valores.get(i)); parcela.setDataVencimento(nota.getDataEmissao().plusDays(origem == null ? 0 : origem.getDias()));
            parcela.setFormaPagamento(origem == null ? null : origem.getFormaPagamento());
            parcela.setFormaPagamentoNome(parcela.getFormaPagamento() == null ? null : parcela.getFormaPagamento().getNome());
            resultado.add(parcela);
        }
        return resultado;
    }

    private void gerarTitulos(NotaEntrada nota) {
        var parcelas = cronograma(nota);
        for (var p : parcelas) {
            var titulo = salvarTitulo(nota, p.getNumeroParcela(), parcelas.size(), p.getValor(), p.getDataVencimento(), p.getFormaPagamento());
            titulo.setCondicaoPagamento(nota.getCondicaoPagamento());
        }
    }

    private ContaPagar salvarTitulo(NotaEntrada nota, int numeroParcela, int totalParcelas,
                                    BigDecimal valor, LocalDate vencimento,
                                    com.kaneko.estacionamento.pagamento.FormaPagamento forma) {
        ContaPagar titulo = new ContaPagar();
        titulo.setOrganizacaoId(nota.getOrganizacaoId());
        titulo.setFornecedor(nota.getFornecedor());
        titulo.setNotaEntrada(nota);
        titulo.setFormaPagamento(forma);
        titulo.setNumeroParcela(numeroParcela);
        titulo.setTotalParcelas(totalParcelas);
        titulo.setValorOriginal(valor);
        titulo.setValorTotal(valor);
        titulo.setValorPago(BigDecimal.ZERO);
        titulo.setValorDesconto(BigDecimal.ZERO);
        titulo.setValorJuros(BigDecimal.ZERO);
        titulo.setValorMulta(BigDecimal.ZERO);
        titulo.setDataEmissao(nota.getDataEmissao());
        titulo.setDataVencimento(vencimento);
        titulo.setSituacao(SituacaoContaPagar.PENDENTE);
        return contaPagarRepository.save(titulo);
    }

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long id) {
        return (organizacaoId == null
                ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : condicaoPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    @Transactional(readOnly = true)
    public List<Long> incluirCompensacoes(Long org, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String marcadores = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var args = new ArrayList<Object>(); args.add(org); args.addAll(ids); args.addAll(ids);
        return jdbc.queryForList("select id from movimento_estoque where organizacao_id=? and (id in ("
                + marcadores + ") or compensado_id in (" + marcadores + ")) order by id", Long.class, args.toArray());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void auditar(NotaEntrada nota, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (nota.getOrganizacaoId() == null) {
            return;
        }
        auditoria.registrar(new RegistrarAuditoriaCommand(nota.getOrganizacaoId(), acao,
                "NOTA_ENTRADA", nota.getId(), null, null, null, anterior, posterior,
                null, nota.getVersion(), ClasseRetencaoAuditoria.FISCAL));
    }

    private static Map<String, Object> snapshot(NotaEntrada nota) {
        return Map.of("situacao", nota.getSituacao().name(),
                "fornecedorId", nota.getFornecedor().getId(),
                "valorTotal", nota.getValorTotal(),
                "localEstoqueId", nota.getLocalEstoque() == null
                        ? 0L : nota.getLocalEstoque().getId());
    }
}
