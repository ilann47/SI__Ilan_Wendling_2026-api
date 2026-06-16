package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.patio.dto.EntradaRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
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

    public MovimentacaoService(MovimentacaoRepository repository,
                               VeiculoRepository veiculoRepository,
                               MensalistaRepository mensalistaRepository,
                               TarifaRepository tarifaRepository) {
        this.repository = repository;
        this.veiculoRepository = veiculoRepository;
        this.mensalistaRepository = mensalistaRepository;
        this.tarifaRepository = tarifaRepository;
    }

    @Transactional(readOnly = true)
    public Page<Movimentacao> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /** Pátio atual: movimentacoes ainda abertas. */
    @Transactional(readOnly = true)
    public Page<Movimentacao> listarPatio(Pageable pageable) {
        return repository.findByStatus(StatusMovimentacao.ABERTO, pageable);
    }

    @Transactional(readOnly = true)
    public Movimentacao buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentacao", id));
    }

    @Transactional
    public Movimentacao registrarEntrada(EntradaRequest req) {
        Veiculo veiculo = buscarVeiculo(req.veiculoId());
        if (Boolean.FALSE.equals(veiculo.getAtivo())) {
            throw new ConflitoException("O veiculo esta inativo e nao pode entrar no patio.");
        }
        if (repository.existsByVeiculoIdAndStatus(veiculo.getId(), StatusMovimentacao.ABERTO)) {
            throw new ConflitoException("Este veiculo ja esta no patio (ha uma movimentacao aberta).");
        }

        Movimentacao mov = new Movimentacao();
        mov.setVeiculo(veiculo);
        mov.setDataEntrada(OffsetDateTime.now());
        mov.setStatus(StatusMovimentacao.ABERTO);

        Optional<Mensalista> contrato =
                mensalistaRepository.findFirstByVeiculoIdAndStatus(veiculo.getId(), StatusMensalista.ATIVO);
        if (contrato.isPresent()) {
            mov.setTipo(TipoMovimentacao.MENSALISTA);
            mov.setMensalista(contrato.get());
        } else {
            mov.setTipo(TipoMovimentacao.AVULSO);
            Tarifa tarifa = tarifaRepository.findFirstByAtivoTrueOrderByIdDesc()
                    .orElseThrow(() -> new ConflitoException(
                            "Nenhuma tarifa vigente cadastrada para a cobranca avulsa."));
            mov.setTarifa(tarifa);
        }
        return repository.save(mov);
    }

    @Transactional
    public Movimentacao registrarSaida(Long id) {
        Movimentacao mov = buscar(id);
        if (mov.getStatus() != StatusMovimentacao.ABERTO) {
            throw new ConflitoException("Esta movimentacao nao esta aberta e nao pode receber saida.");
        }
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
        return repository.save(mov);
    }

    private Veiculo buscarVeiculo(Long veiculoId) {
        return veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo", veiculoId));
    }
}
