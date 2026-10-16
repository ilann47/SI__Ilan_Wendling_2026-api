package com.kaneko.estacionamento.fiscal.importacao;

import com.kaneko.estacionamento.auditoria.*;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.compras.*;
import com.kaneko.estacionamento.compras.dto.ItemRecebimentoCompraRequest;
import com.kaneko.estacionamento.compras.dto.ReceberOrdemCompraRequest;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.fiscal.NotaEntradaService;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaEntradaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaRequest;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordena importação, conciliação, conferência e lançamentos atômicos dos agregados existentes. */
@Service
public class ImportacaoNotaService {
    private final ImportacaoNotaRepository repository;
    private final NotaEntradaXmlParser parser;
    private final OrganizacaoRepository organizacoes;
    private final FornecedorRepository fornecedores;
    private final ProdutoRepository produtos;
    private final LocalEstoqueRepository locais;
    private final CondicaoPagamentoRepository condicoes;
    private final OrdemCompraRepository ordens;
    private final RecebimentoCompraRepository recebimentos;
    private final OrdemCompraService compras;
    private final NotaEntradaService notas;
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ImportacaoNotaService(ImportacaoNotaRepository repository, NotaEntradaXmlParser parser,
            OrganizacaoRepository organizacoes, FornecedorRepository fornecedores,
            ProdutoRepository produtos, LocalEstoqueRepository locais, CondicaoPagamentoRepository condicoes,
            OrdemCompraRepository ordens, RecebimentoCompraRepository recebimentos,
            OrdemCompraService compras, NotaEntradaService notas, JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.repository=repository; this.parser=parser; this.organizacoes=organizacoes;
        this.fornecedores=fornecedores; this.produtos=produtos; this.locais=locais;
        this.condicoes=condicoes; this.ordens=ordens; this.recebimentos=recebimentos;
        this.compras=compras; this.notas=notas; this.jdbc=jdbc; this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    public Page<ResumoImportacaoResponse> listar(Long org, Pageable pageable) {
        return repository.listar(org, pageable);
    }

    @Transactional(readOnly=true)
    public ImportacaoNotaResponse buscar(Long org, Long id) {
        return resposta(repository.findByIdAndOrganizacaoId(id,org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ImportacaoNotaEntrada",id)));
    }

    @Transactional
    public ImportacaoNotaResponse importar(Long org, Long ator, byte[] xml) {
        var documento = parser.ler(xml);
        var organizacao = organizacoes.findByIdAndStatus(org, StatusOrganizacao.ATIVA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao",org));
        if (!organizacao.getDocumento().equals(documento.destinatarioDocumento()))
            throw new ConflitoException("O destinatário do XML não corresponde à organização ativa.");
        if (!"BRL".equals(organizacao.getMoeda()))
            throw new ConflitoException("Esta importação de NF-e exige organização com moeda BRL.");
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "IMPORTACAO_XML:" + org + ":" + documento.chave());
        String hash = hash(xml);
        var existente = repository.findByOrganizacaoIdAndChave(org,documento.chave());
        if (existente.isPresent()) {
            if (!hash.equals(existente.get().getXmlHash()))
                throw new ConflitoException("A chave fiscal já foi importada com outro conteúdo. Consulte a importação existente.");
            return resposta(existente.get());
        }
        var registro = new ImportacaoNotaEntrada();
        registro.setOrganizacaoId(org); registro.setAtorId(ator); registro.setChave(documento.chave());
        registro.setXmlOriginal(xml.clone()); registro.setXmlHash(hash); registro.setNumero(documento.numero());
        registro.setEmitenteNome(documento.emitenteNome()); registro.setTotal(valorExato(documento.total(),2,15));
        registro.setStatus(StatusImportacaoNota.IMPORTADA); registro.setRevisadoEm(OffsetDateTime.now());
        fornecedores.findByOrganizacaoIdAndDocumento(org, documento.emitenteDocumento())
                .filter(f -> Boolean.TRUE.equals(f.getAtivo())).ifPresent(f -> registro.setFornecedorId(f.getId()));
        repository.saveAndFlush(registro);
        auditar(registro,"XML_NOTA_IMPORTADO");
        return resposta(registro);
    }

    @Transactional
    public ImportacaoNotaResponse conciliar(Long org, Long id, long versao, ConciliarImportacaoRequest request) {
        var registro = bloquear(org,id); editavel(registro,versao);
        var documento = parser.ler(registro.getXmlOriginal());
        var fornecedor = fornecedores.findByIdAndOrganizacaoId(request.fornecedorId(),org)
                .filter(f -> Boolean.TRUE.equals(f.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor",request.fornecedorId()));
        if (!fornecedor.getDocumento().replaceAll("[^0-9]", "").equals(documento.emitenteDocumento()))
            throw new ConflitoException("Selecione o fornecedor identificado como emitente do XML.");
        locais.findByIdAndOrganizacaoId(request.localEstoqueId(),org)
                .filter(l -> Boolean.TRUE.equals(l.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("LocalEstoque",request.localEstoqueId()));
        if (request.condicaoPagamentoId()!=null)
            condicoes.findByIdAndOrganizacaoId(request.condicaoPagamentoId(),org)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento",request.condicaoPagamentoId()));
        RecebimentoCompra recebimento = request.recebimentoCompraId()==null ? null : recebimentos
                .findByIdAndOrganizacaoId(request.recebimentoCompraId(),org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("RecebimentoCompra",request.recebimentoCompraId()));
        Long ordemId = request.ordemCompraId()!=null ? request.ordemCompraId()
                : recebimento==null ? null : recebimento.getOrdemCompra().getId();
        OrdemCompra ordem = ordemId==null ? null : ordens.findByIdAndOrganizacaoId(ordemId,org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OrdemCompra",ordemId));
        if (ordem!=null && recebimento==null && !Boolean.TRUE.equals(request.registrarNovoRecebimento()))
            throw new ConflitoException("Selecione o recebimento existente ou confirme explicitamente que esta mercadoria ainda não foi recebida.");
        if (ordem!=null && !ordem.getFornecedor().getId().equals(fornecedor.getId()))
            throw new ConflitoException("O fornecedor da compra diverge do emitente do XML.");
        if (recebimento!=null && (!recebimento.getOrdemCompra().getId().equals(ordemId)
                || !recebimento.getLocalEstoque().getId().equals(request.localEstoqueId())))
            throw new ConflitoException("Pedido ou local diverge do recebimento selecionado.");
        var fonte = documento.itens().stream().collect(Collectors.toMap(DocumentoXmlEntrada.Item::sequencia,Function.identity()));
        if (request.itens().size()!=fonte.size()) throw new ConflitoException("Concilie todos os itens do XML.");
        var existentes = registro.getItens().stream().collect(Collectors.toMap(ItemImportacaoNotaEntrada::getSequencia,Function.identity()));
        Set<Integer> sequencias = new HashSet<>();
        Set<Long> itensCompra = new HashSet<>();
        for (var item : request.itens()) {
            var origem = fonte.get(item.sequencia());
            if (origem==null || !sequencias.add(item.sequencia())) throw new IllegalArgumentException("Sequência de item ausente ou repetida.");
            var produto = produtos.findByIdAndOrganizacaoId(item.produtoId(),org)
                    .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto",item.produtoId()));
            String unidade = produto.getUnidadeMedida().getSigla();
            BigDecimal quantidade = valorExato(origem.quantidade().multiply(item.fatorConversao()),3,15);
            BigDecimal unitario = valorExato(item.valorUnitario(),2,15);
            if (quantidade.multiply(unitario).setScale(2,RoundingMode.HALF_UP).compareTo(origem.valorProdutos())!=0)
                throw new ConflitoException("Preço e quantidade conciliados não preservam o valor do XML no item " + item.sequencia() + ".");
            String motivo = item.motivoDivergencia()==null ? null : item.motivoDivergencia().trim();
            if ((!unidade.equalsIgnoreCase(origem.unidade()) || item.fatorConversao().compareTo(BigDecimal.ONE)!=0)
                    && (motivo==null || motivo.isBlank()))
                throw new ConflitoException("Explique a conversão de unidade no item " + item.sequencia() + ".");
            if (ordem!=null) {
                var comprado = ordem.getItens().stream().filter(i -> i.getId().equals(item.itemOrdemCompraId())).findFirst()
                        .orElseThrow(() -> new ConflitoException("Relacione o item " + item.sequencia() + " ao item do pedido."));
                if (!itensCompra.add(comprado.getId()) || !comprado.getProduto().getId().equals(produto.getId()))
                    throw new ConflitoException("Item do pedido repetido ou com outro produto.");
                if (recebimento==null && quantidade.compareTo(comprado.saldoReceber())>0)
                    throw new ConflitoException("Quantidade do XML excede o saldo pendente da compra. Se já recebeu, selecione esse recebimento.");
                if (unitario.compareTo(comprado.getValorUnitario())!=0 && (motivo==null || motivo.isBlank()))
                    throw new ConflitoException("Registre o motivo da divergência entre preço negociado e XML no item " + item.sequencia() + ".");
            } else if (item.itemOrdemCompraId()!=null) throw new IllegalArgumentException("Selecione o pedido antes de vincular seus itens.");
            var conciliado = existentes.get(item.sequencia());
            if (conciliado==null) { conciliado=new ItemImportacaoNotaEntrada(); conciliado.setSequencia(item.sequencia()); registro.adicionar(conciliado); }
            conciliado.setProduto(produto); conciliado.setUnidadeDestino(unidade); conciliado.setFatorConversao(item.fatorConversao());
            conciliado.setQuantidade(quantidade); conciliado.setValorUnitario(unitario); conciliado.setQuantidadeConferida(null);
            conciliado.setOrdemCompraId(ordemId); conciliado.setItemOrdemCompraId(item.itemOrdemCompraId()); conciliado.setMotivoDivergencia(motivo);
        }
        if (recebimento!=null) validarRecebimento(registro,recebimento);
        registro.setFornecedorId(fornecedor.getId()); registro.setLocalEstoqueId(request.localEstoqueId());
        registro.setCondicaoPagamentoId(request.condicaoPagamentoId()); registro.setOrdemCompraId(ordemId);
        registro.setRecebimentoCompraId(request.recebimentoCompraId()); registro.setConferidoEm(null); registro.setConferidoPor(null);
        registro.setRegistrarNovoRecebimento(ordem!=null && recebimento==null && Boolean.TRUE.equals(request.registrarNovoRecebimento()));
        registro.setStatus(StatusImportacaoNota.CONCILIADA);
        salvar(registro,"XML_NOTA_CONCILIADO");
        return resposta(registro);
    }

    @Transactional
    public ImportacaoNotaResponse conferir(Long org, Long ator, Long id, long versao, ConferirImportacaoRequest request) {
        var registro=bloquear(org,id); editavel(registro,versao);
        if (registro.getStatus()==StatusImportacaoNota.IMPORTADA) throw new ConflitoException("Concilie os produtos antes da conferência física.");
        var itens=registro.getItens().stream().collect(Collectors.toMap(ItemImportacaoNotaEntrada::getSequencia,Function.identity()));
        Set<Integer> vistos=new HashSet<>();
        if (request.itens().size()!=itens.size()) throw new ConflitoException("Informe a contagem de todos os itens.");
        for (var contagem:request.itens()) {
            var item=itens.get(contagem.sequencia());
            if (item==null || !vistos.add(contagem.sequencia())) throw new IllegalArgumentException("Sequência de contagem ausente ou repetida.");
            item.setQuantidadeConferida(contagem.quantidadeConferida());
        }
        boolean confere=registro.getItens().stream().allMatch(i -> i.getQuantidade().compareTo(i.getQuantidadeConferida())==0);
        registro.setStatus(confere ? StatusImportacaoNota.CONFERIDA : StatusImportacaoNota.CONCILIADA);
        registro.setConferidoPor(ator); registro.setConferidoEm(OffsetDateTime.now());
        salvar(registro,"XML_MERCADORIA_CONFERIDA");
        return resposta(registro);
    }

    @Transactional
    public ImportacaoNotaResponse confirmar(Long org,Long ator,Long id,long versao,boolean podeReceberCompra) {
        var registro=bloquear(org,id);
        if (registro.getStatus()==StatusImportacaoNota.CONFIRMADA) return resposta(registro);
        editavel(registro,versao);
        if (registro.getStatus()!=StatusImportacaoNota.CONFERIDA) throw new ConflitoException("Conclua a conciliação e resolva a diferença da contagem antes de confirmar.");
        var documento=parser.ler(registro.getXmlOriginal());
        var divergencias=divergencias(registro,documento);
        if (!divergencias.isEmpty()) throw new ConflitoException(String.join(" ",divergencias));
        if (registro.getOrdemCompraId()!=null && registro.getRecebimentoCompraId()==null) {
            if (!registro.isRegistrarNovoRecebimento()) throw new ConflitoException("O novo recebimento precisa ser confirmado explicitamente na conciliação.");
            if (!podeReceberCompra) throw new AuthorizationDeniedException("É necessária permissão para receber a compra.");
            var ordem=ordens.findParaAtualizar(registro.getOrdemCompraId(),org)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("OrdemCompra",registro.getOrdemCompraId()));
            var request=new ReceberOrdemCompraRequest(registro.getLocalEstoqueId(),registro.getConferidoEm(),
                    "Conferência do XML " + registro.getChave(),registro.getItens().stream()
                            .map(i -> new ItemRecebimentoCompraRequest(i.getItemOrdemCompraId(),i.getQuantidade())).toList());
            var resultado=compras.receber(org,ator,ordem.getId(),ordem.getVersion(),"xml-"+id,request);
            registro.setRecebimentoCompraId(resultado.recebimento().getId());
        }
        if (registro.getRecebimentoCompraId()!=null) {
            var recebido=recebimentos.findParaAtualizar(registro.getRecebimentoCompraId(),org)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("RecebimentoCompra",registro.getRecebimentoCompraId()));
            validarRecebimento(registro,recebido);
        }
        var fonte=documento.itens().stream().collect(Collectors.toMap(DocumentoXmlEntrada.Item::sequencia,Function.identity()));
        var itens=registro.getItens().stream().map(i -> new ItemNotaEntradaRequest(i.getProduto().getId(),
                i.getQuantidade(),i.getValorUnitario(),fonte.get(i.getSequencia()).desconto(),null)).toList();
        var nota=notas.criar(org,new NotaEntradaRequest(documento.numero(),documento.modelo(),documento.serie(),
                registro.getFornecedorId(),registro.getCondicaoPagamentoId(),registro.getLocalEstoqueId(),registro.getRecebimentoCompraId(),
                documento.emissao(),registro.getConferidoEm().toLocalDate(),null,documento.frete(),documento.seguro(),
                documento.outrasDespesas(),BigDecimal.ZERO,"Importação XML " + documento.chave(),itens));
        if (nota.getValorTotal().compareTo(documento.total())!=0) throw new ConflitoException("O total calculado diverge do XML. Nenhum lançamento foi confirmado.");
        notas.confirmar(org,ator,nota.getId());
        registro.setNotaEntradaId(nota.getId()); registro.setStatus(StatusImportacaoNota.CONFIRMADA);
        salvar(registro,"XML_NOTA_CONFIRMADO");
        return resposta(registro);
    }

    private static void validarRecebimento(ImportacaoNotaEntrada registro,RecebimentoCompra recebimento) {
        if (registro.getItens().size()!=recebimento.getItens().size()) throw new ConflitoException("Os itens do XML devem corresponder a todos os itens do recebimento selecionado.");
        for (var item:registro.getItens()) {
            var recebido=recebimento.getItens().stream().filter(r -> r.getItemOrdemCompra().getId().equals(item.getItemOrdemCompraId())).findFirst()
                    .orElseThrow(() -> new ConflitoException("O item conciliado não pertence ao recebimento selecionado."));
            if (!recebido.getProduto().getId().equals(item.getProduto().getId()) || recebido.getQuantidade().compareTo(item.getQuantidade())!=0)
                throw new ConflitoException("Produto ou quantidade diverge do recebimento já registrado. Não será repetida a entrada de estoque.");
        }
    }
    private ImportacaoNotaEntrada bloquear(Long org,Long id) {
        return repository.bloquear(id,org).orElseThrow(() -> new RecursoNaoEncontradoException("ImportacaoNotaEntrada",id));
    }
    private static void editavel(ImportacaoNotaEntrada registro,long versao) {
        if (registro.getStatus()==StatusImportacaoNota.CONFIRMADA) throw new ConflitoException("Importação confirmada não pode ser alterada.");
        if (registro.getVersion()!=versao) throw new VersaoImportacaoException();
    }
    private void salvar(ImportacaoNotaEntrada registro,String acao) {
        registro.setRevisadoEm(OffsetDateTime.now()); repository.saveAndFlush(registro); auditar(registro,acao);
    }
    private void auditar(ImportacaoNotaEntrada registro,String acao) {
        auditoria.registrar(new RegistrarAuditoriaCommand(registro.getOrganizacaoId(),acao,"IMPORTACAO_XML_NOTA",registro.getId(),
                null,null,null,null,Map.of("status",registro.getStatus().name(),"chave",registro.getChave(),
                "notaEntradaId",registro.getNotaEntradaId()==null ? 0L : registro.getNotaEntradaId()),null,registro.getVersion(),ClasseRetencaoAuditoria.FISCAL));
    }
    private ImportacaoNotaResponse resposta(ImportacaoNotaEntrada registro) {
        var documento=parser.ler(registro.getXmlOriginal());
        return new ImportacaoNotaResponse(registro.getId(),registro.getVersion(),registro.getStatus(),documento,
                registro.getFornecedorId(),registro.getLocalEstoqueId(),registro.getCondicaoPagamentoId(),registro.getOrdemCompraId(),
                registro.getRecebimentoCompraId(),registro.getNotaEntradaId(),registro.isRegistrarNovoRecebimento(),registro.getItens().stream().map(i ->
                    new ImportacaoNotaResponse.Item(i.getSequencia(),i.getProduto().getId(),i.getProduto().getNome(),i.getUnidadeDestino(),
                            i.getFatorConversao(),i.getQuantidade(),i.getValorUnitario(),i.getQuantidadeConferida(),i.getItemOrdemCompraId(),i.getMotivoDivergencia())).toList(),
                divergencias(registro,documento));
    }
    private static List<String> divergencias(ImportacaoNotaEntrada registro,DocumentoXmlEntrada documento) {
        var problemas=new ArrayList<String>();
        var calculado=documento.itens().stream().map(i -> i.valorProdutos().subtract(i.desconto())).reduce(BigDecimal.ZERO,BigDecimal::add)
                .add(documento.frete()).add(documento.seguro()).add(documento.outrasDespesas());
        if (calculado.compareTo(documento.total())!=0)
            problemas.add("O total fiscal contém componentes não representados pelo cálculo atual da nota interna. A importação está preservada; a confirmação exige suporte explícito a esses componentes, sem ajustar valores artificialmente.");
        for (var i:registro.getItens()) if (i.getQuantidadeConferida()!=null && i.getQuantidade().compareTo(i.getQuantidadeConferida())!=0)
            problemas.add("Item " + i.getSequencia() + ": quantidade contada diverge da quantidade conciliada.");
        return List.copyOf(problemas);
    }
    private static BigDecimal valorExato(BigDecimal valor,int escala,int precisao) {
        try {
            var normalizado=valor.setScale(escala,RoundingMode.UNNECESSARY);
            if (normalizado.precision()>precisao) throw new ArithmeticException();
            return normalizado;
        } catch (ArithmeticException e) { throw new ConflitoException("O valor não cabe na precisão atual do estoque/nota. Concilie unidade e preço sem perder centavos."); }
    }
    private static String hash(byte[] xml) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
