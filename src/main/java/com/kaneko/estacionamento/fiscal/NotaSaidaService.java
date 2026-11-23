package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaSaidaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de negocio da nota fiscal de saida (venda). Na confirmacao, valida que
 * ha estoque suficiente, baixa o estoque dos produtos e gera os titulos a
 * receber conforme a condicao de pagamento.
 */
@Service
public class NotaSaidaService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA = 6;
    private static final int PRECISAO_VALOR = 19;
    private static final int PRECISAO_QUANTIDADE = 18;
    private static final int PRECISAO_PERCENTUAL = 9;

    private final NotaSaidaRepository repository;
    private final ClienteRepository clienteRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final ProdutoRepository produtoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final EstoqueService estoqueService;
    private final AuditoriaService auditoria;
    private final IdempotenciaRepository idempotencia;

    public NotaSaidaService(NotaSaidaRepository repository,
                            ClienteRepository clienteRepository,
                            CondicaoPagamentoRepository condicaoPagamentoRepository,
                            ProdutoRepository produtoRepository,
                            ContaReceberRepository contaReceberRepository,
                            LocalEstoqueRepository localEstoqueRepository,
                            EstoqueService estoqueService,
                            AuditoriaService auditoria,
                            IdempotenciaRepository idempotencia) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.produtoRepository = produtoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.estoqueService = estoqueService;
        this.auditoria = auditoria;
        this.idempotencia = idempotencia;
    }

    @Transactional(readOnly = true)
    public Page<NotaSaida> listar(Long organizacaoId, Pageable pageable) {
        return listar(organizacaoId, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NotaSaida> listar(Long organizacaoId, String numero, SituacaoNota situacao, Pageable pageable) {
        String filtro = numero == null ? "" : numero.strip();
        Page<Long> ids = filtro.isEmpty() && situacao == null ? (organizacaoId == null
                ? repository.findIdsByOrganizacaoIdIsNull(pageable)
                : repository.findIdsByOrganizacaoId(organizacaoId, pageable))
                : repository.filtrarIds(organizacaoId, filtro, situacao, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<Long, NotaSaida> notasPorId = repository.findAllByIdIn(ids.getContent())
                .stream().collect(java.util.stream.Collectors.toMap(NotaSaida::getId, nota -> nota));
        List<NotaSaida> notasOrdenadas = ids.getContent().stream()
                .map(notasPorId::get)
                .toList();
        return new PageImpl<>(notasOrdenadas, pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotaSaida buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaSaida", id));
    }

    @Transactional
    public NotaSaida criar(Long organizacaoId, NotaSaidaRequest req) {
        NotaSaida nota = repository.saveAndFlush(preparar(organizacaoId, req, null));
        auditar(nota, "NOTA_SAIDA_CRIADA", null, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaSaida criarIdempotente(Long org, Long ator, String chave, NotaSaidaRequest req) {
        if (org == null || ator == null || chave.isBlank() || chave.length() > 128) {
            throw new IllegalArgumentException("A chave de registro exige organização ativa e entre 1 e 128 caracteres.");
        }
        String operacao = "CRIAR_NOTA_SAIDA";
        String hash;
        try {
            // Mantém as chaves já gravadas para notas diretas antes da mudança do DTO.
            String corpo = req.toString().replaceFirst("(, localEstoqueId=[^,]+), dataEmissao=",
                    "$1, vendaAdministrativaId=null, dataEmissao=");
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
        idempotencia.bloquear(org, ator, operacao, chave);
        var anterior = idempotencia.buscar(org, ator, operacao, chave);
        if (anterior.isPresent()) {
            if (!hash.equals(anterior.get().requestHash())) {
                throw new ConflitoException("A chave de registro já foi usada com outros dados.");
            }
            return buscar(org, anterior.get().recursoId());
        }
        NotaSaida nota = criar(org, req);
        idempotencia.registrar(org, ator, operacao, chave, hash, "NOTA_SAIDA", nota.getId());
        return nota;
    }

    @Transactional
    public NotaSaida atualizar(Long org, Long id, long versao, NotaSaidaRequest req) {
        NotaSaida atual = bloquear(org, id);
        exigirVersao(atual, versao);
        if (atual.getSituacao() != SituacaoNota.PENDENTE) {
            throw new ConflitoException("Somente notas pendentes podem ser alteradas.");
        }
        NotaSaida nova = preparar(org, req, id);
        Map<String, Object> anterior = snapshot(atual);
        atual.setNumero(nova.getNumero());
        atual.setModelo(nova.getModelo());
        atual.setSerie(nova.getSerie());
        atual.setCliente(nova.getCliente());
        atual.setCondicaoPagamento(nova.getCondicaoPagamento());
        atual.setLocalEstoque(nova.getLocalEstoque());
        atual.setDataEmissao(nova.getDataEmissao());
        atual.setDataSaida(nova.getDataSaida());
        atual.setTipoFrete(nova.getTipoFrete());
        atual.setValorProdutos(nova.getValorProdutos());
        atual.setValorFrete(nova.getValorFrete());
        atual.setValorSeguro(nova.getValorSeguro());
        atual.setOutrasDespesas(nova.getOutrasDespesas());
        atual.setValorDesconto(nova.getValorDesconto());
        atual.setValorTotal(nova.getValorTotal());
        atual.setObservacao(nova.getObservacao());
        atual.getItens().clear();
        repository.flush();
        nova.getItens().forEach(atual::addItem);
        atual.getParcelas().clear();
        atual.getParcelas().addAll(nova.getParcelas());
        atual.setCronogramaDefinido(true);
        atual.setUpdatedAt(OffsetDateTime.now());
        atual = repository.saveAndFlush(atual);
        auditar(atual, "NOTA_SAIDA_ATUALIZADA", anterior, snapshot(atual));
        return atual;
    }

    private NotaSaida preparar(Long organizacaoId, NotaSaidaRequest req, Long ignorarId) {
        Cliente cliente = (organizacaoId == null
                ? clienteRepository.findByIdAndOrganizacaoIdIsNull(req.clienteId())
                : clienteRepository.findByIdAndOrganizacaoId(req.clienteId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        if (Boolean.FALSE.equals(cliente.getAtivo())) {
            throw new ConflitoException("O cliente está inativo e não pode receber notas.");
        }

        NotaSaida nota = new NotaSaida();
        nota.setOrganizacaoId(organizacaoId);
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo().trim() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie().trim() : "1");
        nota.setCliente(cliente);
        nota.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? buscarCondicao(organizacaoId, req.condicaoPagamentoId()) : null);
        if (organizacaoId != null) {
            if (req.localEstoqueId() == null) {
                throw new IllegalArgumentException("O local de estoque e obrigatorio.");
            }
            LocalEstoque local = localEstoqueRepository
                    .findByIdAndOrganizacaoId(req.localEstoqueId(), organizacaoId)
                    .filter(l -> Boolean.TRUE.equals(l.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "LocalEstoque", req.localEstoqueId()));
            nota.setLocalEstoque(local);
        }
        boolean duplicada = organizacaoId == null
                ? repository.existeIdentidadeLegada(nota.getNumero(), nota.getSerie(), cliente.getId(), ignorarId)
                : repository.existeIdentidade(organizacaoId, nota.getNumero(), nota.getSerie(), nota.getModelo(), ignorarId);
        if (duplicada) {
            throw new ConflitoException("Já existe uma nota de saída com esta identificação.");
        }

        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        nota.setDataEmissao(emissao);
        if (req.dataSaida() != null && req.dataSaida().isBefore(emissao)) {
            throw new ConflitoException("A data de saída não pode ser anterior à emissão.");
        }
        nota.setDataSaida(req.dataSaida());
        nota.setTipoFrete(req.tipoFrete() != null ? req.tipoFrete() : TipoFrete.CIF);

        BigDecimal frete = valorExato(nz(req.valorFrete()), PRECISAO_VALOR);
        BigDecimal seguro = valorExato(nz(req.valorSeguro()), PRECISAO_VALOR);
        BigDecimal outras = valorExato(nz(req.outrasDespesas()), PRECISAO_VALOR);
        BigDecimal descontoNota = valorExato(nz(req.valorDesconto()), PRECISAO_VALOR);
        nota.setValorFrete(frete);
        nota.setValorSeguro(seguro);
        nota.setOutrasDespesas(outras);
        nota.setValorDesconto(descontoNota);
        nota.setObservacao(req.observacao());
        nota.setSituacao(SituacaoNota.PENDENTE);

        BigDecimal valorProdutos = BigDecimal.ZERO;
        int seq = 1;
        for (ItemNotaSaidaRequest ir : req.itens()) {
            Produto produto = (organizacaoId == null
                    ? produtoRepository.findByIdAndOrganizacaoIdIsNull(ir.produtoId())
                    : produtoRepository.findByIdAndOrganizacaoId(ir.produtoId(), organizacaoId))
                    .filter(p -> !Boolean.FALSE.equals(p.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto ativo", ir.produtoId()));
            ItemNotaSaida item = new ItemNotaSaida();
            item.setOrganizacaoId(organizacaoId);
            item.setProduto(produto);
            item.setSequencia(seq++);
            item.setQuantidade(valorExato(ir.quantidade(), PRECISAO_QUANTIDADE));
            if (item.getQuantidade().signum() == 0) {
                throw new ConflitoException("A quantidade deve ser positiva.");
            }
            item.setValorUnitario(valorExato(ir.valorUnitario(), PRECISAO_VALOR));
            BigDecimal bruto = item.getQuantidade().multiply(item.getValorUnitario()).setScale(ESCALA, RoundingMode.HALF_UP);
            BigDecimal percentual = valorExato(nz(ir.percentualDesconto()), PRECISAO_PERCENTUAL);
            if (percentual.compareTo(CEM) > 0) {
                throw new ConflitoException("O percentual de desconto não pode exceder 100%.");
            }
            BigDecimal porPercentual = bruto.multiply(percentual).divide(CEM, ESCALA, RoundingMode.HALF_UP);
            BigDecimal descItem = ir.valorDesconto() == null ? porPercentual : valorExato(ir.valorDesconto(), PRECISAO_VALOR);
            if (ir.percentualDesconto() != null && ir.valorDesconto() != null && descItem.compareTo(porPercentual) != 0) {
                throw new ConflitoException("O valor e o percentual de desconto do item são contraditórios.");
            }
            item.setValorDesconto(descItem);
            item.setPercentualDesconto(ir.percentualDesconto() != null ? percentual : bruto.signum() == 0
                    ? BigDecimal.ZERO : descItem.multiply(CEM).divide(bruto, ESCALA, RoundingMode.HALF_UP));
            BigDecimal totalItem = bruto.subtract(descItem);
            if (totalItem.signum() < 0) {
                throw new ConflitoException("O desconto do item não pode exceder o seu valor.");
            }
            item.setValorTotal(valorExato(totalItem, PRECISAO_VALOR));
            item.setCustoPrecoFinal(nz(produto.getCusto()));
            nota.addItem(item);
            valorProdutos = valorProdutos.add(totalItem);
        }
        nota.setValorProdutos(valorExato(valorProdutos, PRECISAO_VALOR));

        BigDecimal total = valorProdutos.add(frete).add(seguro).add(outras)
                .subtract(descontoNota).setScale(ESCALA, RoundingMode.HALF_UP);
        if (total.signum() < 0) {
            throw new ConflitoException("O desconto da nota não pode exceder o seu valor.");
        }
        nota.setValorTotal(valorExato(total, PRECISAO_VALOR));

        ratearDespesas(nota.getItens(), valorProdutos, frete, seguro, outras);
        nota.getParcelas().addAll(calcularParcelas(nota, true));
        nota.setCronogramaDefinido(true);
        return nota;
    }

    /**
     * Confirma a nota: valida o estoque disponivel, baixa a quantidade vendida e
     * gera os titulos a receber.
     */
    @Transactional
    public NotaSaida confirmar(Long organizacaoId, Long atorId, Long id) {
        return confirmar(organizacaoId, atorId, id, null);
    }

    @Transactional
    public NotaSaida confirmar(Long organizacaoId, Long atorId, Long id, Long versao) {
        NotaSaida nota = bloquear(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNota.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser confirmadas.");
        }
        if (versao != null) exigirVersao(nota, versao);
        if (organizacaoId != null && !nota.isCronogramaDefinido()) {
            throw new ConflitoException("Salve o rascunho novamente para conferir as parcelas antes de confirmar.");
        }
        if (organizacaoId != null) {
            estoqueService.bloquearProdutos(organizacaoId,
                    nota.getItens().stream().map(item -> item.getProduto().getId()).toList());
        }
        for (ItemNotaSaida item : nota.getItens()) {
            Produto produto = item.getProduto();
            if (organizacaoId == null) {
                if (produto.getQuantidade().compareTo(item.getQuantidade()) < 0) {
                    throw new ConflitoException("Estoque insuficiente para o produto '" + produto.getNome()
                            + "' (disponível: " + produto.getQuantidade().stripTrailingZeros().toPlainString() + ").");
                }
                item.setCustoPrecoFinal(nz(produto.getCusto()));
                produto.setQuantidade(produto.getQuantidade().subtract(item.getQuantidade()));
                produtoRepository.saveAndFlush(produto);
            } else {
                BigDecimal custo = estoqueService.registrarBaixa(organizacaoId, atorId, produto.getId(),
                        nota.getLocalEstoque().getId(), item.getQuantidade(),
                        "NOTA_SAIDA",
                        nota.getId() + ":" + item.getSequencia(),
                        "Confirmacao da nota de saida " + nota.getNumero());
                item.setCustoPrecoFinal(custo);
            }
        }
        gerarTitulos(nota);
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SAIDA_CONFIRMADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaSaidaResponse confirmarComResposta(Long organizacaoId, Long atorId, Long id, Long versao) {
        return NotaSaidaResponse.from(confirmar(organizacaoId, atorId, id, versao));
    }

    @Transactional
    public NotaSaidaResponse cancelarComResposta(Long organizacaoId, Long id, Long versao) {
        return NotaSaidaResponse.from(cancelar(organizacaoId, id, versao));
    }

    @Transactional
    public NotaSaida cancelar(Long organizacaoId, Long id, Long versao) {
        NotaSaida nota = bloquear(organizacaoId, id);
        if (versao != null) exigirVersao(nota, versao);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser cancelada.");
        }
        if (nota.getRevertidaEm() != null) {
            throw new ConflitoException("Uma nota revertida deve permanecer no histórico.");
        }
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CANCELADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SAIDA_CANCELADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id, Long versao) {
        NotaSaida nota = bloquear(organizacaoId, id);
        if (versao != null) exigirVersao(nota, versao);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser excluída.");
        }
        if (nota.getRevertidaEm() != null) {
            throw new ConflitoException("Uma nota revertida não pode ser excluída.");
        }
        Map<String, Object> anterior = snapshot(nota);
        repository.delete(nota);
        repository.flush();
        auditar(nota, "NOTA_SAIDA_EXCLUIDA", anterior, null);
    }

    /** Distribui frete/seguro/outras despesas proporcionalmente ao valor de cada item. */
    private void ratearDespesas(List<ItemNotaSaida> itens, BigDecimal base,
                                BigDecimal frete, BigDecimal seguro, BigDecimal outras) {
        var pesos = itens.stream().map(i -> base.signum() > 0 ? i.getValorTotal() : i.getQuantidade()).toList();
        var fretes = RateioMonetario.distribuirEmMilionesimos(frete, pesos);
        var seguros = RateioMonetario.distribuirEmMilionesimos(seguro, pesos);
        var despesas = RateioMonetario.distribuirEmMilionesimos(outras, pesos);
        for (int i = 0; i < itens.size(); i++) {
            itens.get(i).setRateioFrete(fretes.get(i));
            itens.get(i).setRateioSeguro(seguros.get(i));
            itens.get(i).setRateioOutras(despesas.get(i));
        }
    }

    private List<ParcelaNotaSaida> calcularParcelas(NotaSaida nota, boolean emMilionesimos) {
        if (nota.getValorTotal().signum() == 0) return List.of();
        List<ParcelaCondicaoPagamento> condicoes = nota.getCondicaoPagamento() == null ? List.of()
                : nota.getCondicaoPagamento().getParcelas().stream()
                    .sorted(Comparator.comparing(ParcelaCondicaoPagamento::getNumero)).toList();
        if (!condicoes.isEmpty() && condicoes.stream().map(ParcelaCondicaoPagamento::getPercentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(CEM) != 0) {
            throw new ConflitoException("A soma das parcelas da condição deve ser 100%.");
        }
        var pesos = condicoes.isEmpty() ? List.of(BigDecimal.ONE)
                : condicoes.stream().map(ParcelaCondicaoPagamento::getPercentual).toList();
        var valores = emMilionesimos ? RateioMonetario.distribuirEmMilionesimos(nota.getValorTotal(), pesos)
                : RateioMonetario.distribuir(nota.getValorTotal(), pesos);
        var resultado = new java.util.ArrayList<ParcelaNotaSaida>();
        for (int i = 0; i < valores.size(); i++) {
            if (valores.get(i).signum() == 0) continue;
            var origem = condicoes.isEmpty() ? null : condicoes.get(i);
            var parcela = new ParcelaNotaSaida();
            parcela.setNumeroParcela(resultado.size() + 1);
            parcela.setValor(valores.get(i));
            parcela.setDataVencimento(nota.getDataEmissao().plusDays(origem == null ? 0 : origem.getDias()));
            parcela.setFormaPagamento(origem == null ? null : origem.getFormaPagamento());
            parcela.setFormaPagamentoNome(parcela.getFormaPagamento() == null ? null : parcela.getFormaPagamento().getNome());
            resultado.add(parcela);
        }
        return resultado;
    }

    private void gerarTitulos(NotaSaida nota) {
        List<ParcelaNotaSaida> parcelas = nota.isCronogramaDefinido() ? nota.getParcelas() : calcularParcelas(nota, false);
        for (var parcela : parcelas) {
            salvarTitulo(nota, parcela.getNumeroParcela(), parcelas.size(), parcela.getValor(),
                    parcela.getDataVencimento(), parcela.getFormaPagamento());
        }
    }

    private ContaReceber salvarTitulo(NotaSaida nota, int numeroParcela, int totalParcelas,
                                      BigDecimal valor, LocalDate vencimento,
                                      com.kaneko.estacionamento.pagamento.FormaPagamento forma) {
        ContaReceber titulo = new ContaReceber();
        titulo.setOrganizacaoId(nota.getOrganizacaoId());
        titulo.setCliente(nota.getCliente());
        titulo.setNotaSaida(nota);
        titulo.setCondicaoPagamento(nota.getCondicaoPagamento());
        titulo.setFormaPagamento(forma);
        titulo.setNumeroParcela(numeroParcela);
        titulo.setTotalParcelas(totalParcelas);
        titulo.setValorOriginal(valor);
        titulo.setValorTotal(valor);
        titulo.setValorRecebido(BigDecimal.ZERO);
        titulo.setDataEmissao(nota.getDataEmissao());
        titulo.setDataVencimento(vencimento);
        titulo.setSituacao(SituacaoContaReceber.PENDENTE);
        return contaReceberRepository.save(titulo);
    }

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long id) {
        return (organizacaoId == null
                ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : condicaoPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal valorExato(BigDecimal valor, int precisao) {
        try {
            BigDecimal exato = valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
            if (exato.signum() < 0 || exato.precision() > precisao) throw new ArithmeticException();
            return exato;
        } catch (ArithmeticException | NullPointerException ex) {
            throw new ConflitoException("Valor inválido: informe até 6 casas decimais, dentro do limite do campo.");
        }
    }

    private NotaSaida bloquear(Long org, Long id) {
        return org == null ? buscar(null, id) : repository.findParaAtualizar(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaSaida", id));
    }

    private static void exigirVersao(NotaSaida nota, long versao) {
        if (nota.getVersion() == null || nota.getVersion() != versao) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "A nota foi alterada. Recarregue e revise a versão atual.");
        }
    }

    private void auditar(NotaSaida nota, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (nota.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(nota.getOrganizacaoId(), acao,
                "NOTA_SAIDA", nota.getId(), null, null, null, anterior, posterior,
                null, nota.getVersion(), ClasseRetencaoAuditoria.FISCAL));
    }

    private static Map<String, Object> snapshot(NotaSaida nota) {
        return Map.of("situacao", nota.getSituacao().name(),
                "clienteId", nota.getCliente().getId(), "valorTotal", nota.getValorTotal(),
                "localEstoqueId", nota.getLocalEstoque() == null
                        ? 0L : nota.getLocalEstoque().getId());
    }
}
