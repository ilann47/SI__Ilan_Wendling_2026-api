package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.FormaPagamentoRepository;
import com.kaneko.estacionamento.patio.MensalistaRepository;
import com.kaneko.estacionamento.patio.MovimentacaoRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    public NotaServicoService(NotaServicoRepository repository,
                              ClienteRepository clienteRepository,
                              ServicoRepository servicoRepository,
                              MovimentacaoRepository movimentacaoRepository,
                              MensalistaRepository mensalistaRepository,
                              CondicaoPagamentoRepository condicaoPagamentoRepository,
                              FormaPagamentoRepository formaPagamentoRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.servicoRepository = servicoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.mensalistaRepository = mensalistaRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotaServico> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public NotaServico buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaServico", id));
    }

    @Transactional
    public NotaServico criar(NotaServicoRequest req) {
        NotaServico nota = new NotaServico();
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie() : "1");
        if (repository.existsByNumeroAndSerie(nota.getNumero(), nota.getSerie())) {
            throw new ConflitoException("Já existe uma nota de serviço com este número/série.");
        }
        aplicar(nota, req);
        nota.setSituacao(SituacaoNotaServico.PENDENTE);
        return repository.save(nota);
    }

    @Transactional
    public NotaServico atualizar(Long id, NotaServicoRequest req) {
        NotaServico nota = buscar(id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser alteradas.");
        }
        aplicar(nota, req);
        return repository.save(nota);
    }

    @Transactional
    public NotaServico emitir(Long id) {
        NotaServico nota = buscar(id);
        if (nota.getSituacao() != SituacaoNotaServico.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser emitidas.");
        }
        nota.setSituacao(SituacaoNotaServico.EMITIDA);
        return repository.save(nota);
    }

    @Transactional
    public NotaServico cancelar(Long id) {
        NotaServico nota = buscar(id);
        if (nota.getSituacao() == SituacaoNotaServico.CANCELADA) {
            throw new ConflitoException("A nota já está cancelada.");
        }
        nota.setSituacao(SituacaoNotaServico.CANCELADA);
        return repository.save(nota);
    }

    @Transactional
    public void excluir(Long id) {
        NotaServico nota = buscar(id);
        if (nota.getSituacao() == SituacaoNotaServico.EMITIDA) {
            throw new ConflitoException("Uma nota emitida não pode ser excluída.");
        }
        repository.delete(nota);
    }

    private void aplicar(NotaServico nota, NotaServicoRequest req) {
        Cliente cliente = clienteRepository.findById(req.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        nota.setCliente(cliente);
        nota.setServico(req.servicoId() != null
                ? servicoRepository.findById(req.servicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico", req.servicoId())) : null);
        nota.setMovimentacao(req.movimentacaoId() != null
                ? movimentacaoRepository.findById(req.movimentacaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentacao", req.movimentacaoId())) : null);
        nota.setMensalista(req.mensalistaId() != null
                ? mensalistaRepository.findById(req.mensalistaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensalista", req.mensalistaId())) : null);
        if (nota.getMovimentacao() != null && nota.getMensalista() != null) {
            throw new ConflitoException("A origem não pode ser movimentação e mensalista ao mesmo tempo.");
        }
        nota.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(req.condicaoPagamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", req.condicaoPagamentoId())) : null);
        nota.setFormaPagamento(req.formaPagamentoId() != null
                ? formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(req.formaPagamentoId())
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
        nota.setObservacao(req.observacao());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
