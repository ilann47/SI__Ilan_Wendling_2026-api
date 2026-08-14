package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public NotaServico atualizar(Long organizacaoId, Long id, NotaServicoRequest req) {
        NotaServico nota = buscar(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser alteradas.");
        }
        Map<String, Object> anterior = snapshot(nota);
        aplicar(organizacaoId, nota, req);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_ATUALIZADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaServico emitir(Long organizacaoId, Long id) {
        NotaServico nota = buscar(organizacaoId, id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser emitidas.");
        }
        Map<String, Object> anterior = snapshot(nota);
        gerarTitulos(nota);
        nota.setSituacao(SituacaoNotaServico.EMITIDA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_EMITIDA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaServico cancelar(Long organizacaoId, Long id) {
        NotaServico nota = buscar(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNotaServico.CANCELADA) {
            throw new ConflitoException("A nota já está cancelada.");
        }
        List<ContaReceber> titulos = nota.getOrganizacaoId() == null
                ? contaReceberRepository.findByNotaServicoIdAndOrganizacaoIdIsNull(nota.getId())
                : contaReceberRepository.findByNotaServicoIdAndOrganizacaoId(
                        nota.getId(), nota.getOrganizacaoId());
        if (titulos.stream().anyMatch(t -> t.getValorRecebido().signum() > 0)) {
            throw new ConflitoException("Nota com titulo recebido nao pode ser cancelada.");
        }
        Map<String, Object> anterior = snapshot(nota);
        titulos.forEach(t -> t.setSituacao(SituacaoContaReceber.CANCELADA));
        contaReceberRepository.saveAll(titulos);
        nota.setSituacao(SituacaoNotaServico.CANCELADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SERVICO_CANCELADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        NotaServico nota = buscar(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNotaServico.EMITIDA) {
            throw new ConflitoException("Uma nota emitida não pode ser excluída.");
        }
        Map<String, Object> anterior = snapshot(nota);
        repository.delete(nota);
        repository.flush();
        auditar(nota, "NOTA_SERVICO_EXCLUIDA", anterior, null);
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
        OrdemServico ordemServico = req.ordemServicoId() == null ? null
                : ordemServicoRepository.findByIdAndOrganizacaoId(
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
        nota.setDataEmissao(emissao);

        BigDecimal valorServico = req.valorServico();
        BigDecimal aliquota = nz(req.aliquotaIss());
        BigDecimal desconto = nz(req.valorDesconto());
        if (desconto.compareTo(valorServico) > 0) {
            throw new ConflitoException("O desconto não pode exceder o valor do serviço.");
        }
        nota.setValorServico(valorServico);
        nota.setAliquotaIss(aliquota);
        nota.setValorIss(valorServico.multiply(aliquota).divide(CEM, 2, RoundingMode.HALF_UP));
        nota.setValorDesconto(desconto);
        nota.setValorTotal(valorServico.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
        if (ordemServico != null
                && nota.getValorTotal().compareTo(ordemServico.getValorTotal()) != 0) {
            throw new ConflitoException("O valor total da nota diverge da ordem de servico.");
        }
        nota.setObservacao(req.observacao());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void gerarTitulos(NotaServico nota) {
        List<ParcelaCondicaoPagamento> parcelas = nota.getCondicaoPagamento() == null
                ? List.of() : nota.getCondicaoPagamento().getParcelas().stream()
                        .sorted(Comparator.comparing(ParcelaCondicaoPagamento::getNumero)).toList();
        if (parcelas.isEmpty()) {
            salvarTitulo(nota, 1, 1, nota.getValorTotal(), nota.getDataEmissao(),
                    nota.getFormaPagamento());
            return;
        }
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < parcelas.size(); i++) {
            ParcelaCondicaoPagamento parcela = parcelas.get(i);
            BigDecimal valor = i == parcelas.size() - 1
                    ? nota.getValorTotal().subtract(acumulado)
                    : nota.getValorTotal().multiply(parcela.getPercentual())
                            .divide(CEM, 2, RoundingMode.HALF_UP);
            acumulado = acumulado.add(valor);
            salvarTitulo(nota, i + 1, parcelas.size(), valor,
                    nota.getDataEmissao().plusDays(parcela.getDias()),
                    parcela.getFormaPagamento() != null
                            ? parcela.getFormaPagamento() : nota.getFormaPagamento());
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
                "valorTotal", nota.getValorTotal());
    }
}
