package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.patio.dto.EntradaRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operacao do patio: registro de entrada e saida de veiculos, com calculo da
 * cobranca avulsa.
 *
 * <p>Na entrada, o tipo e determinado automaticamente: se o veiculo possui um
 * contrato de mensalista ATIVO, a movimentacao e MENSALISTA; caso contrario,
 * AVULSO, aplicando-se a tarifa vigente. A regra de uma unica movimentacao
 * aberta por veiculo espelha {@code uq_mov_veiculo_aberta}.
 */
@Service
public class MovimentacaoService {

    private final MovimentacaoRepository repository;
    private final VeiculoRepository veiculoRepository;
    private final MensalistaRepository mensalistaRepository;
    private final TarifaRepository tarifaRepository;
    private final PatioRepository patioRepository;
    private final AuditoriaService auditoria;

    public MovimentacaoService(MovimentacaoRepository repository,
                               VeiculoRepository veiculoRepository,
                               MensalistaRepository mensalistaRepository,
                               TarifaRepository tarifaRepository,
                               PatioRepository patioRepository,
                               AuditoriaService auditoria) {
        this.repository = repository;
        this.veiculoRepository = veiculoRepository;
        this.mensalistaRepository = mensalistaRepository;
        this.tarifaRepository = tarifaRepository;
        this.patioRepository = patioRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<Movimentacao> listar(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findByOrganizacaoIdIsNull(pageable)
                : repository.findByOrganizacaoId(organizacaoId, pageable);
    }

    /** Pátio atual: movimentacoes ainda abertas. */
    @Transactional(readOnly = true)
    public Page<Movimentacao> listarPatio(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findByStatus(StatusMovimentacao.ABERTO, pageable)
                : repository.findByOrganizacaoIdAndStatus(
                        organizacaoId, StatusMovimentacao.ABERTO, pageable);
    }

    @Transactional(readOnly = true)
    public Movimentacao buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentacao", id));
    }

    @Transactional
    public Movimentacao registrarEntrada(Long organizacaoId, EntradaRequest req) {
        Veiculo veiculo = buscarVeiculo(organizacaoId, req.veiculoId());
        if (Boolean.FALSE.equals(veiculo.getAtivo())) {
            throw new ConflitoException("O veiculo esta inativo e nao pode entrar no patio.");
        }
        boolean aberta = organizacaoId == null
                ? repository.existsByVeiculoIdAndStatus(veiculo.getId(), StatusMovimentacao.ABERTO)
                : repository.existsByOrganizacaoIdAndVeiculoIdAndStatus(
                        organizacaoId, veiculo.getId(), StatusMovimentacao.ABERTO);
        if (aberta) {
            throw new ConflitoException("Este veiculo ja esta no patio (ha uma movimentacao aberta).");
        }

        Movimentacao mov = new Movimentacao();
        mov.setOrganizacaoId(organizacaoId);
        mov.setVeiculo(veiculo);
        mov.setDataEntrada(OffsetDateTime.now());
        mov.setStatus(StatusMovimentacao.ABERTO);
        if (req.patioId() != null) {
            mov.setPatio((organizacaoId == null ? patioRepository.findById(req.patioId())
                    : patioRepository.findByIdAndOrganizacaoId(req.patioId(), organizacaoId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Patio", req.patioId())));
        }

        Optional<Mensalista> contrato =
                organizacaoId == null
                        ? mensalistaRepository.findFirstByVeiculoIdAndStatus(
                                veiculo.getId(), StatusMensalista.ATIVO)
                        : mensalistaRepository.findFirstByOrganizacaoIdAndVeiculoIdAndStatus(
                                organizacaoId, veiculo.getId(), StatusMensalista.ATIVO);
        if (contrato.isPresent()) {
            mov.setTipo(TipoMovimentacao.MENSALISTA);
            mov.setMensalista(contrato.get());
        } else {
            mov.setTipo(TipoMovimentacao.AVULSO);
            Tarifa tarifa = (organizacaoId == null
                    ? tarifaRepository.findFirstByAtivoTrueOrderByIdDesc()
                    : tarifaRepository.findFirstByOrganizacaoIdAndAtivoTrueOrderByIdDesc(organizacaoId))
                    .orElseThrow(() -> new ConflitoException(
                            "Nenhuma tarifa vigente cadastrada para a cobranca avulsa."));
            mov.setTarifa(tarifa);
        }
        mov = repository.saveAndFlush(mov);
        auditar(mov, "MOVIMENTACAO_ABERTA", null, snapshot(mov));
        return mov;
    }

    @Transactional
    public Movimentacao registrarSaida(Long organizacaoId, Long id) {
        Movimentacao mov = buscar(organizacaoId, id);
        if (mov.getStatus() != StatusMovimentacao.ABERTO) {
            throw new ConflitoException("Esta movimentacao nao esta aberta e nao pode receber saida.");
        }
        Map<String, Object> anterior = snapshot(mov);
        OffsetDateTime saida = OffsetDateTime.now();

        BigDecimal valor;
        if (mov.getTipo() == TipoMovimentacao.MENSALISTA) {
            valor = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            Tarifa t = mov.getTarifa();
            valor = CalculoTarifa.calcular(
                    t.getValorHora(), t.getValorDiaria(), t.getToleranciaMin(), mov.getDataEntrada(), saida);
        }

        mov.setDataSaida(saida);
        mov.setValorCobrado(valor);
        mov.setStatus(StatusMovimentacao.FECHADO);
        mov = repository.saveAndFlush(mov);
        auditar(mov, "MOVIMENTACAO_FECHADA", anterior, snapshot(mov));
        return mov;
    }

    /**
     * Cancela uma movimentacao ABERTA (entrada registrada por engano). Apenas
     * tickets ABERTOS podem ser cancelados — uma movimentacao ja FECHADA exige
     * estorno (fora do escopo). Espelha o RF 4.18.3 (Cancelar Movimentacao).
     */
    @Transactional
    public Movimentacao cancelar(Long organizacaoId, Long id) {
        Movimentacao mov = buscar(organizacaoId, id);
        if (mov.getStatus() != StatusMovimentacao.ABERTO) {
            throw new ConflitoException(
                    "Apenas uma movimentacao ABERTA pode ser cancelada.");
        }
        Map<String, Object> anterior = snapshot(mov);
        mov.setStatus(StatusMovimentacao.CANCELADO);
        mov = repository.saveAndFlush(mov);
        auditar(mov, "MOVIMENTACAO_CANCELADA", anterior, snapshot(mov));
        return mov;
    }

    private Veiculo buscarVeiculo(Long organizacaoId, Long veiculoId) {
        return (organizacaoId == null ? veiculoRepository.findByIdAndOrganizacaoIdIsNull(veiculoId)
                : veiculoRepository.findByIdAndOrganizacaoId(veiculoId, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo", veiculoId));
    }

    private void auditar(Movimentacao mov, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (mov.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(mov.getOrganizacaoId(), acao,
                "MOVIMENTACAO", mov.getId(), null,
                mov.getPatio() != null ? mov.getPatio().getId() : null,
                null, anterior, posterior, null, mov.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
    }

    private static Map<String, Object> snapshot(Movimentacao mov) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("veiculoId", mov.getVeiculo().getId());
        dados.put("patioId", mov.getPatio() != null ? mov.getPatio().getId() : null);
        dados.put("mensalistaId", mov.getMensalista() != null ? mov.getMensalista().getId() : null);
        dados.put("tarifaId", mov.getTarifa() != null ? mov.getTarifa().getId() : null);
        dados.put("tipo", mov.getTipo().name());
        dados.put("status", mov.getStatus().name());
        dados.put("dataEntrada", mov.getDataEntrada());
        dados.put("dataSaida", mov.getDataSaida());
        dados.put("valorCobrado", mov.getValorCobrado());
        return dados;
    }
}
