package com.kaneko.estacionamento.fiscal.importacao;

import com.kaneko.estacionamento.auditoria.*;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
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
import java.time.LocalDate;
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
    private final NotaEntradaService notas;
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ImportacaoNotaService(ImportacaoNotaRepository repository, NotaEntradaXmlParser parser,
            OrganizacaoRepository organizacoes, FornecedorRepository fornecedores,
            ProdutoRepository produtos, LocalEstoqueRepository locais, CondicaoPagamentoRepository condicoes,
            NotaEntradaService notas, JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.repository=repository; this.parser=parser; this.organizacoes=organizacoes;
        this.fornecedores=fornecedores; this.produtos=produtos; this.locais=locais;
        this.condicoes=condicoes; this.notas=notas; this.jdbc=jdbc; this.auditoria=auditoria;
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
        NotaEntradaService.validarDataEmissao(documento.emissao(), LocalDate.now());
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
        var fonte = documento.itens().stream().collect(Collectors.toMap(DocumentoXmlEntrada.Item::sequencia,Function.identity()));
        if (request.itens().size()!=fonte.size()) throw new ConflitoException("Concilie todos os itens do XML.");
        var existentes = registro.getItens().stream().collect(Collectors.toMap(ItemImportacaoNotaEntrada::getSequencia,Function.identity()));
        Set<Integer> sequencias = new HashSet<>();
        for (var item : request.itens()) {
            var origem = fonte.get(item.sequencia());
            if (origem==null || !sequencias.add(item.sequencia())) throw new IllegalArgumentException("Sequência de item ausente ou repetida.");
            var produto = produtos.findByIdAndOrganizacaoId(item.produtoId(),org)
                    .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto",item.produtoId()));
            String unidade = produto.getUnidadeMedida().getSigla();
            BigDecimal quantidade = valorExato(origem.quantidade().multiply(item.fatorConversao()),6,18);
            BigDecimal unitario = valorExato(item.valorUnitario(),6,19);
            if (quantidade.multiply(unitario).setScale(2,RoundingMode.HALF_UP).compareTo(origem.valorProdutos())!=0)
                throw new ConflitoException("Preço e quantidade conciliados não preservam o valor do XML no item " + item.sequencia() + ".");
            String motivo = item.motivoDivergencia()==null ? null : item.motivoDivergencia().trim();
            if ((!unidade.equalsIgnoreCase(origem.unidade()) || item.fatorConversao().compareTo(BigDecimal.ONE)!=0)
                    && (motivo==null || motivo.isBlank()))
                throw new ConflitoException("Explique a conversão de unidade no item " + item.sequencia() + ".");
            var conciliado = existentes.get(item.sequencia());
            if (conciliado==null) { conciliado=new ItemImportacaoNotaEntrada(); conciliado.setSequencia(item.sequencia()); registro.adicionar(conciliado); }
            conciliado.setProduto(produto); conciliado.setUnidadeDestino(unidade); conciliado.setFatorConversao(item.fatorConversao());
            conciliado.setQuantidade(quantidade); conciliado.setValorUnitario(unitario); conciliado.setQuantidadeConferida(null);
            conciliado.setMotivoDivergencia(motivo);
        }
        registro.setFornecedorId(fornecedor.getId()); registro.setLocalEstoqueId(request.localEstoqueId());
        registro.setCondicaoPagamentoId(request.condicaoPagamentoId());
        registro.setConferidoEm(null); registro.setConferidoPor(null);
        registro.getParcelas().clear(); registro.setCronogramaDefinido(false);
        registro.setStatus(StatusImportacaoNota.CONCILIADA);
        salvar(registro,"XML_NOTA_CONCILIADO");
        return resposta(registro);
    }

    @Transactional
    public ImportacaoNotaResponse conferir(Long org, Long ator, Long id, long versao, ConferirImportacaoRequest request) {
        var registro=bloquear(org,id); editavel(registro,versao);
        if (registro.getStatus()==StatusImportacaoNota.IMPORTADA) throw new ConflitoException("Concilie os produtos antes da conferência física.");
        var itens=registro.getItens().stream().collect(Collectors.toMap(ItemImportacaoNotaEntrada::getSequencia,Function.identity()));
        Map<Integer,BigDecimal> contagens=new LinkedHashMap<>();
        if (request.itens().size()!=itens.size()) throw new ConflitoException("Informe a contagem de todos os itens.");
        for (var contagem:request.itens()) {
            if (!itens.containsKey(contagem.sequencia()) || contagens.putIfAbsent(contagem.sequencia(),contagem.quantidadeConferida())!=null)
                throw new IllegalArgumentException("Sequencia de contagem ausente ou repetida.");
        }
        boolean confere=registro.getItens().stream().allMatch(i -> i.getQuantidade().compareTo(contagens.get(i.getSequencia()))==0);
        var quando=OffsetDateTime.now();
        var documento=parser.ler(registro.getXmlOriginal());
        // Calcular antes de sujar o agregado evita autoflush/versionamento intermediario.
        var proposta=confere && valorRepresentado(documento).compareTo(documento.total())==0
                ? notas.prepararConferencia(org,requisicao(registro,documento,quando.toLocalDate())) : null;
        for(var item:registro.getItens()) item.setQuantidadeConferida(contagens.get(item.getSequencia()));
        registro.setStatus(confere ? StatusImportacaoNota.CONFERIDA : StatusImportacaoNota.CONCILIADA);
        registro.setConferidoPor(ator); registro.setConferidoEm(quando);
        registro.getParcelas().clear(); registro.setCronogramaDefinido(proposta!=null);
        if (proposta!=null) {
            registro.getParcelas().addAll(NotaEntradaService.copiarCronograma(proposta.getParcelas()));
            registro.setCondicaoPagamentoId(proposta.getCondicaoPagamento()==null ? null : proposta.getCondicaoPagamento().getId());
        }
        salvar(registro,"XML_MERCADORIA_CONFERIDA");
        return resposta(registro);
    }

    @Transactional
    public ImportacaoNotaResponse confirmar(Long org,Long ator,Long id,long versao) {
        var registro=bloquear(org,id);
        if (registro.getStatus()==StatusImportacaoNota.CONFIRMADA) return resposta(registro);
        editavel(registro,versao);
        if (registro.getStatus()!=StatusImportacaoNota.CONFERIDA) throw new ConflitoException("Conclua a conciliação e resolva a diferença da contagem antes de confirmar.");
        var documento=parser.ler(registro.getXmlOriginal());
        var divergencias=divergencias(registro,documento);
        if (!divergencias.isEmpty()) throw new ConflitoException(String.join(" ",divergencias));
        if (!registro.isCronogramaDefinido())
            throw new ConflitoException("Refaca a conferencia para congelar a previsao financeira desta importacao.");
        var nota=notas.criarComCronograma(org,requisicao(registro,documento),registro.getParcelas());
        if (nota.getValorTotal().compareTo(documento.total())!=0) throw new ConflitoException("O total calculado diverge do XML. Nenhum lançamento foi confirmado.");
        notas.confirmar(org,ator,nota.getId());
        registro.setNotaEntradaId(nota.getId()); registro.setStatus(StatusImportacaoNota.CONFIRMADA);
        salvar(registro,"XML_NOTA_CONFIRMADO");
        return resposta(registro);
    }

    @Transactional
    public com.kaneko.estacionamento.fiscal.dto.PreviaNotaEntradaResponse previa(Long org, Long id) {
        var registro = repository.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ImportacaoNotaEntrada", id));
        if (registro.getStatus() == StatusImportacaoNota.CONFIRMADA)
            return notas.previaPersistida(org, registro.getNotaEntradaId());
        if (registro.getStatus() != StatusImportacaoNota.CONFERIDA)
            throw new ConflitoException("Conclua a conciliacao e a conferencia antes da previa da nota.");
        var documento = parser.ler(registro.getXmlOriginal());
        var problemas = divergencias(registro, documento);
        if (!problemas.isEmpty()) throw new ConflitoException(String.join(" ", problemas));
        if (!registro.isCronogramaDefinido())
            throw new ConflitoException("Refaca a conferencia para congelar a previsao financeira desta importacao.");
        return notas.previaComCronograma(org, requisicao(registro, documento), registro.getParcelas());
    }

    private static NotaEntradaRequest requisicao(ImportacaoNotaEntrada registro, DocumentoXmlEntrada documento) {
        return requisicao(registro,documento,registro.getConferidoEm().toLocalDate());
    }

    private static NotaEntradaRequest requisicao(ImportacaoNotaEntrada registro, DocumentoXmlEntrada documento, java.time.LocalDate chegada) {
        var fonte=documento.itens().stream().collect(Collectors.toMap(DocumentoXmlEntrada.Item::sequencia,Function.identity()));
        var itens=registro.getItens().stream().map(i -> new ItemNotaEntradaRequest(i.getProduto().getId(),
                i.getQuantidade(),i.getValorUnitario(),fonte.get(i.getSequencia()).desconto(),null)).toList();
        return new NotaEntradaRequest(documento.numero(),documento.modelo(),documento.serie(),
                registro.getFornecedorId(),registro.getCondicaoPagamentoId(),registro.getLocalEstoqueId(),
                documento.emissao(),chegada,null,documento.frete(),documento.seguro(),
                documento.outrasDespesas(),BigDecimal.ZERO,"Importação XML " + documento.chave(),itens);
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
                registro.getFornecedorId(),registro.getLocalEstoqueId(),registro.getCondicaoPagamentoId(),
                registro.getNotaEntradaId(),registro.getItens().stream().map(i ->
                    new ImportacaoNotaResponse.Item(i.getSequencia(),i.getProduto().getId(),i.getProduto().getNome(),i.getUnidadeDestino(),
                            i.getFatorConversao(),i.getQuantidade(),i.getValorUnitario(),i.getQuantidadeConferida(),i.getMotivoDivergencia())).toList(),
                divergencias(registro,documento));
    }
    private static List<String> divergencias(ImportacaoNotaEntrada registro,DocumentoXmlEntrada documento) {
        var problemas=new ArrayList<String>();
        var calculado=valorRepresentado(documento);
        if (calculado.compareTo(documento.total())!=0)
            problemas.add("O total fiscal contém componentes não representados pelo cálculo atual da nota interna. A importação está preservada; a confirmação exige suporte explícito a esses componentes, sem ajustar valores artificialmente.");
        for (var i:registro.getItens()) if (i.getQuantidadeConferida()!=null && i.getQuantidade().compareTo(i.getQuantidadeConferida())!=0)
            problemas.add("Item " + i.getSequencia() + ": quantidade contada diverge da quantidade conciliada.");
        return List.copyOf(problemas);
    }
    private static BigDecimal valorRepresentado(DocumentoXmlEntrada documento) {
        return documento.itens().stream().map(i -> i.valorProdutos().subtract(i.desconto())).reduce(BigDecimal.ZERO,BigDecimal::add)
                .add(documento.frete()).add(documento.seguro()).add(documento.outrasDespesas());
    }

    private static BigDecimal valorExato(BigDecimal valor,int escala,int precisao) {
        try {
            var normalizado=valor.setScale(escala,RoundingMode.UNNECESSARY);
            if (normalizado.precision()>precisao) throw new ArithmeticException();
            return normalizado;
        } catch (ArithmeticException e) { throw new ConflitoException("Concilie unidade e preço sem perder precisão: use até seis casas decimais."); }
    }
    private static String hash(byte[] xml) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
