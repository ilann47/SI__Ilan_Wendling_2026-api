package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaServicoResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoDetalheResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoResponse;
import com.kaneko.estacionamento.fiscal.dto.ParcelaNotaEntradaResponse;
import com.kaneko.estacionamento.pagamento.FormaPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.FormaPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import com.kaneko.estacionamento.patio.MensalistaRepository;
import com.kaneko.estacionamento.patio.MovimentacaoRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.ordensservico.OrdemServico;
import com.kaneko.estacionamento.ordensservico.OrdemServicoRepository;
import com.kaneko.estacionamento.ordensservico.StatusOrdemServico;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de negocio da nota de servico (NFS-e): calcula o ISS e o total, e
 * controla o ciclo PENDENTE -> EMITIDA -> CANCELADA.
 */
@Service
public class NotaServicoService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final NotaServicoRepository repository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MensalistaRepository mensalistaRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final AuditoriaService auditoria;

    public NotaServicoService(NotaServicoRepository repository,
                              ClienteRepository clienteRepository,
                              ServicoRepository servicoRepository,
                              MovimentacaoRepository movimentacaoRepository,
                              MensalistaRepository mensalistaRepository,
                              CondicaoPagamentoRepository condicaoPagamentoRepository,
                              FormaPagamentoRepository formaPagamentoRepository,
                              OrdemServicoRepository ordemServicoRepository,
                              ContaReceberRepository contaReceberRepository,
                              AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.servicoRepository = servicoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.mensalistaRepository = mensalistaRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.ordemServicoRepository = ordemServicoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<NotaServico> listar(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findAllByOrganizacaoIdIsNull(pageable)
                : repository.findAllByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NotaServicoResponse> consultar(Long org, String numero, Long clienteId,
            SituacaoNotaServico situacao, Pageable pageable) {
        String busca = numero == null ? "" : numero.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return repository.findAll((root, query, cb) -> {
            var filtro = org == null ? cb.isNull(root.get("organizacaoId"))
                    : cb.equal(root.get("organizacaoId"), org);
            if (!busca.isEmpty()) filtro = cb.and(filtro,
                    cb.like(cb.lower(root.get("numero")), "%" + busca + "%", '\\'));
            if (clienteId != null) filtro = cb.and(filtro, cb.equal(root.get("cliente").get("id"), clienteId));
            if (situacao != null) filtro = cb.and(filtro, cb.equal(root.get("situacao"), situacao));
            return filtro;
        }, pageable).map(NotaServicoResponse::from);
    }

    @Transactional(readOnly = true)
    public NotaServicoDetalheResponse detalhes(Long org, Long id, boolean consultarFinanceiro) {
        var nota = NotaServicoResponse.from(buscar(org, id));
        var contas = !consultarFinanceiro ? null : (org == null
                ? contaReceberRepository.findByNotaServicoIdAndOrganizacaoIdIsNull(id)
                : contaReceberRepository.findByNotaServicoIdAndOrganizacaoId(id, org))
                .stream().sorted(Comparator.comparing(ContaReceber::getNumeroParcela))
                .map(ContaReceberResponse::from).toList();
        return new NotaServicoDetalheResponse(nota, contas);
    }

    @Transactional(readOnly = true)
    public NotaServico buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaServico", id));
    }

    @Transactional
    public NotaServico criar(Long organizacaoId, NotaServicoRequest req) {
        NotaServico nota = new NotaServico();
        nota.setOrganizacaoId(organizacaoId);
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie() : "1");
        boolean duplicada = organizacaoId == null
                ? repository.existsByOrganizacaoIdIsNullAndNumeroAndSerie(nota.getNumero(), nota.getSerie())
                : repository.existsByOrganizacaoIdAndNumeroAndSerie(organizacaoId, nota.getNumero(), nota.getSerie());
        if (duplicada) {
            throw new ConflitoException("Já existe uma nota de serviço com este número/série.");
        }
        aplicar(organizacaoId, nota, req);
        nota.setSituacao(SituacaoNotaServico.PENDENTE);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_CRIADA", null, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaServico atualizar(Long organizacaoId, Long id, Long versao, NotaServicoRequest req) {
        NotaServico nota = bloquear(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser alteradas.");
        }
        exigirVersao(nota, versao);
        Map<String, Object> anterior = snapshot(nota);
        aplicar(organizacaoId, nota, req);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_ATUALIZADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaServicoResponse emitir(Long organizacaoId, Long id, Long versao) {
        NotaServico nota = bloquear(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser emitidas.");
        }
        exigirVersao(nota, versao);
        validarEmissao(nota.getDataEmissao());
        if (!nota.isCronogramaDefinido()) {
            throw new ConflitoException("Salve o rascunho novamente para registrar as parcelas antes de emitir.");
        }
        if (organizacaoId != null && nota.getItens().isEmpty()) {
            throw new ConflitoException("Inclua pelo menos um serviço antes de emitir.");
        }
        if (nota.getValorTotal().signum() <= 0) {
            throw new ConflitoException("O total da nota deve ser maior que zero para emitir.");
        }
        Map<String, Object> anterior = snapshot(nota);
        gerarTitulos(nota);
        nota.setSituacao(SituacaoNotaServico.EMITIDA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_EMITIDA", anterior, snapshot(nota));
        return NotaServicoResponse.from(nota);
    }

    @Transactional
    public NotaServicoResponse cancelar(Long organizacaoId, Long id, Long versao, String motivo) {
        NotaServico nota = bloquear(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNotaServico.CANCELADA) {
            throw new ConflitoException("A nota já está cancelada.");
        }
        exigirVersao(nota, versao);
        List<ContaReceber> titulos = contaReceberRepository
                .findIdsByNotaServicoIdAndOrganizacaoId(nota.getId(), nota.getOrganizacaoId())
                .stream().map(tituloId -> contaReceberRepository
                        .buscarParaBaixa(tituloId, organizacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("ContaReceber", tituloId)))
                .toList();
        if (titulos.stream().anyMatch(t -> t.getValorRecebido().signum() > 0)) {
            throw new ConflitoException("Nota com titulo recebido nao pode ser cancelada.");
        }
        Map<String, Object> anterior = snapshot(nota);
        titulos.forEach(t -> t.setSituacao(SituacaoContaReceber.CANCELADA));
        contaReceberRepository.saveAll(titulos);
        nota.setSituacao(SituacaoNotaServico.CANCELADA);
        nota.setMotivoCancelamento(motivo.strip());
        nota.setCanceladaEm(OffsetDateTime.now());
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_CANCELADA", anterior, snapshot(nota));
        return NotaServicoResponse.from(nota);
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id, Long versao) {
        NotaServico nota = bloquear(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Somente notas pendentes podem ser excluídas.");
        }
        exigirVersao(nota, versao);
        Map<String, Object> anterior = snapshot(nota);
        repository.delete(nota);
        repository.flush();
        auditar(nota, "NOTA_SERVICO_EXCLUIDA", anterior, null);
    }

    private NotaServico bloquear(Long organizacaoId, Long id) {
        return repository.findParaAtualizar(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaServico", id));
    }

    private void aplicar(Long organizacaoId, NotaServico nota, NotaServicoRequest req) {
        Cliente cliente = (organizacaoId == null
                ? clienteRepository.findByIdAndOrganizacaoIdIsNull(req.clienteId())
                : clienteRepository.findByIdAndOrganizacaoId(req.clienteId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        nota.setCliente(cliente);
        nota.setServico(req.servicoId() != null
                ? (organizacaoId == null
                    ? servicoRepository.findByIdAndOrganizacaoIdIsNull(req.servicoId())
                    : servicoRepository.findByIdAndOrganizacaoId(req.servicoId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico", req.servicoId())) : null);
        if (organizacaoId != null && (req.movimentacaoId() != null || req.mensalistaId() != null)) {
            throw new ConflitoException(
                    "Origens de patio so podem ser usadas apos sua migracao tenant-aware.");
        }
        nota.setMovimentacao(req.movimentacaoId() != null
                ? movimentacaoRepository.findById(req.movimentacaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentacao", req.movimentacaoId())) : null);
        nota.setMensalista(req.mensalistaId() != null
                ? mensalistaRepository.findById(req.mensalistaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensalista", req.mensalistaId())) : null);
        Long ordemAtual = nota.getOrdemServico() == null ? null : nota.getOrdemServico().getId();
        if (ordemAtual != null && req.ordemServicoId() != null && !ordemAtual.equals(req.ordemServicoId())) {
            throw new ConflitoException("Não é possível trocar a ordem de serviço da nota.");
        }
        OrdemServico ordemServico = req.ordemServicoId() == null ? nota.getOrdemServico()
                : ordemServicoRepository.findParaAtualizar(
                        req.ordemServicoId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "OrdemServico", req.ordemServicoId()));
        if (ordemServico != null) {
            if (repository.existsByOrdemServicoId(ordemServico.getId())
                    && (nota.getOrdemServico() == null
                    || !nota.getOrdemServico().getId().equals(ordemServico.getId()))) {
                throw new ConflitoException("A ordem de servico ja possui nota vinculada.");
            }
            if (ordemServico.getStatus() != StatusOrdemServico.CONCLUIDA) {
                throw new ConflitoException("A ordem de servico precisa estar concluida para faturamento.");
            }
            if (!ordemServico.getCliente().getId().equals(cliente.getId())) {
                throw new ConflitoException("O cliente da nota diverge da ordem de servico.");
            }
            if (nota.getServico() != null && ordemServico.getItens().stream()
                    .noneMatch(i -> i.getServico().getId().equals(nota.getServico().getId()))) {
                throw new ConflitoException("O servico da nota nao pertence a ordem informada.");
            }
        }
        nota.setOrdemServico(ordemServico);
        if (nota.getMovimentacao() != null && nota.getMensalista() != null) {
            throw new ConflitoException("A origem não pode ser movimentação e mensalista ao mesmo tempo.");
        }
        nota.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? (organizacaoId == null
                    ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(req.condicaoPagamentoId())
                    : condicaoPagamentoRepository.findByIdAndOrganizacaoId(
                            req.condicaoPagamentoId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", req.condicaoPagamentoId())) : null);
        nota.setFormaPagamento(req.formaPagamentoId() != null
                ? (organizacaoId == null
                    ? formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(req.formaPagamentoId())
                    : formaPagamentoRepository.findByIdAndOrganizacaoId(
                            req.formaPagamentoId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", req.formaPagamentoId())) : null);

        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        validarEmissao(emissao);
        nota.setDataEmissao(emissao);

        List<ItemNotaServicoResponse> itens = itensDaNota(nota, ordemServico, req);
        BigDecimal valorServico = itens.isEmpty() ? nz(req.valorServico())
                : itens.stream().map(ItemNotaServicoResponse::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aliquota = nz(req.aliquotaIss());
        BigDecimal desconto = ordemServico == null ? nz(req.valorDesconto()) : ordemServico.getValorDesconto();
        if (ordemServico != null && req.valorServico() != null
                && req.valorServico().subtract(nz(req.valorDesconto())).compareTo(ordemServico.getValorTotal()) != 0) {
            throw new ConflitoException("O valor informado diverge da ordem de serviço.");
        }
        if (valorServico.compareTo(new BigDecimal("9999999999999.999999")) > 0) {
            throw new ConflitoException("O valor dos serviços excede o limite da nota.");
        }
        if (desconto.compareTo(valorServico) > 0) {
            throw new ConflitoException("O desconto não pode exceder o valor do serviço.");
        }
        nota.setValorServico(valorServico);
        nota.setAliquotaIss(aliquota);
        nota.setValorIss(valorServico.multiply(aliquota).divide(CEM, MilionesimosConverter.ESCALA, RoundingMode.HALF_UP));
        nota.setValorDesconto(desconto);
        nota.setValorTotal(valorServico.subtract(desconto));
        if (ordemServico != null
                && nota.getValorTotal().compareTo(ordemServico.getValorTotal()) != 0) {
            throw new ConflitoException("O valor total da nota diverge da ordem de servico.");
        }
        nota.setItens(itens);
        nota.setParcelas(planejarParcelas(nota));
        nota.setCronogramaDefinido(true);
        nota.setObservacao(req.observacao());
    }

    private List<ItemNotaServicoResponse> itensDaNota(NotaServico nota, OrdemServico ordem, NotaServicoRequest req) {
        if (ordem != null) {
            if (req.itens() != null && !req.itens().isEmpty())
                throw new ConflitoException("Os itens de uma nota vinculada vêm da ordem de serviço.");
            if (!nota.getItens().isEmpty()) return nota.getItens();
            var ordenados = ordem.getItens().stream().sorted(Comparator
                    .comparing(com.kaneko.estacionamento.ordensservico.ItemOrdemServico::getSequencia)
                    .thenComparing(com.kaneko.estacionamento.ordensservico.ItemOrdemServico::getId)).toList();
            return java.util.stream.IntStream.range(0, ordenados.size()).mapToObj(i -> {
                var item = ordenados.get(i);
                return new ItemNotaServicoResponse(i + 1, item.getServico().getId(), item.getDescricaoSnapshot(),
                        item.getQuantidade(), item.getValorUnitario(), item.getValorDesconto(), item.getValorTotal());
            }).toList();
        }
        if (req.itens() == null) {
            if (nota.getServico() != null && req.valorServico() != null)
                return List.of(new ItemNotaServicoResponse(1, nota.getServico().getId(), nota.getServico().getNome(),
                        BigDecimal.ONE, req.valorServico(), BigDecimal.ZERO, req.valorServico()));
            if (!nota.getItens().isEmpty()) throw new ConflitoException("Informe os serviços da nota.");
            return List.of();
        }
        if (req.itens().isEmpty()) throw new ConflitoException("Inclua pelo menos um serviço na nota.");
        var itens = new ArrayList<ItemNotaServicoResponse>();
        Long org = nota.getOrganizacaoId();
        for (var item : req.itens()) {
            var servico = (org == null ? servicoRepository.findByIdAndOrganizacaoIdIsNull(item.servicoId())
                    : servicoRepository.findByIdAndOrganizacaoId(item.servicoId(), org))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Servico", item.servicoId()));
            var bruto = item.quantidade().multiply(item.valorUnitario())
                    .setScale(MilionesimosConverter.ESCALA, RoundingMode.HALF_UP);
            var desconto = nz(item.valorDesconto());
            if (desconto.compareTo(bruto) > 0)
                throw new ConflitoException("O desconto de um serviço não pode exceder o valor do item.");
            itens.add(new ItemNotaServicoResponse(itens.size() + 1, servico.getId(), servico.getNome(),
                    item.quantidade(), item.valorUnitario(), desconto, bruto.subtract(desconto)));
        }
        return List.copyOf(itens);
    }

    private static void validarEmissao(LocalDate emissao) {
        if (emissao.isAfter(LocalDate.now()))
            throw new ConflitoException("A data de emissão não pode ser futura.");
    }

    private static void exigirVersao(NotaServico nota, Long versao) {
        if (versao != null && !versao.equals(nota.getVersion()))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "A nota mudou. Recarregue e confira a versão atual.");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private List<ParcelaNotaEntradaResponse> planejarParcelas(NotaServico nota) {
        if (nota.getValorTotal().signum() == 0) return List.of();
        List<ParcelaCondicaoPagamento> parcelas = nota.getCondicaoPagamento() == null
                ? List.of() : nota.getCondicaoPagamento().getParcelas().stream()
                        .sorted(Comparator.comparing(ParcelaCondicaoPagamento::getNumero)).toList();
        if (parcelas.isEmpty()) {
            return List.of(parcela(1, 1, nota.getValorTotal(), nota.getDataEmissao(), nota.getFormaPagamento()));
        }
        var percentuais = parcelas.stream().map(ParcelaCondicaoPagamento::getPercentual).toList();
        if (percentuais.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(CEM) != 0)
            throw new ConflitoException("A condição de pagamento deve somar 100% nas parcelas.");
        var valores = RateioMonetario.distribuirEmMilionesimos(nota.getValorTotal(), percentuais);
        int efetivas = (int) valores.stream().filter(valor -> valor.signum() > 0).count();
        var resultado = new ArrayList<ParcelaNotaEntradaResponse>();
        for (int i = 0; i < parcelas.size(); i++) {
            if (valores.get(i).signum() == 0) continue;
            var regra = parcelas.get(i);
            if (regra.getDias() < 0) throw new ConflitoException("O vencimento não pode ser anterior à emissão.");
            resultado.add(parcela(resultado.size() + 1, efetivas, valores.get(i),
                    nota.getDataEmissao().plusDays(regra.getDias()),
                    regra.getFormaPagamento() != null ? regra.getFormaPagamento() : nota.getFormaPagamento()));
        }
        return List.copyOf(resultado);
    }

    private static ParcelaNotaEntradaResponse parcela(int numero, int total, BigDecimal valor,
            LocalDate data, FormaPagamento forma) {
        return new ParcelaNotaEntradaResponse(numero, total, valor, data,
                forma == null ? null : forma.getId(), forma == null ? null : forma.getNome());
    }

    private void gerarTitulos(NotaServico nota) {
        Long org = nota.getOrganizacaoId();
        for (var parcela : nota.getParcelas()) {
            Long formaId = parcela.formaPagamentoId();
            FormaPagamento forma = formaId == null ? null : (org == null
                    ? formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(formaId)
                    : formaPagamentoRepository.findByIdAndOrganizacaoId(formaId, org))
                    .orElseThrow(() -> new ConflitoException(
                            "Uma forma de pagamento da conferência não está disponível. Salve e confira novamente."));
            salvarTitulo(nota, parcela.numeroParcela(), parcela.totalParcelas(), parcela.valor(),
                    parcela.dataVencimento(), forma);
        }
    }

    private void salvarTitulo(NotaServico nota, int numero, int total, BigDecimal valor,
            LocalDate vencimento, com.kaneko.estacionamento.pagamento.FormaPagamento forma) {
        ContaReceber titulo = new ContaReceber();
        titulo.setOrganizacaoId(nota.getOrganizacaoId());
        titulo.setCliente(nota.getCliente());
        titulo.setNotaServico(nota);
        titulo.setCondicaoPagamento(nota.getCondicaoPagamento());
        titulo.setFormaPagamento(forma);
        titulo.setNumeroParcela(numero);
        titulo.setTotalParcelas(total);
        titulo.setValorOriginal(valor);
        titulo.setValorTotal(valor);
        titulo.setValorRecebido(BigDecimal.ZERO);
        titulo.setDataEmissao(nota.getDataEmissao());
        titulo.setDataVencimento(vencimento);
        titulo.setSituacao(SituacaoContaReceber.PENDENTE);
        contaReceberRepository.save(titulo);
    }

    private void auditar(NotaServico nota, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (nota.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(nota.getOrganizacaoId(), acao,
                "NOTA_SERVICO", nota.getId(), null, null, null, anterior, posterior,
                null, nota.getVersion(), ClasseRetencaoAuditoria.FISCAL));
    }

    private static Map<String, Object> snapshot(NotaServico nota) {
        return Map.of("situacao", nota.getSituacao().name(),
                "clienteId", nota.getCliente().getId(),
                "servicoId", nota.getServico() == null ? 0L : nota.getServico().getId(),
                "valorTotal", nota.getValorTotal(),
                "motivoCancelamento", nota.getMotivoCancelamento() == null ? "" : nota.getMotivoCancelamento());
    }
}
