package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.logistica.dto.TransportadoraVeiculoFrotaRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do vinculo transportadora-veiculo (frota): existencia das
 * pontas e unicidade do par.
 */
@Service
public class TransportadoraVeiculoFrotaService {

    private final TransportadoraVeiculoFrotaRepository repository;
    private final TransportadoraRepository transportadoraRepository;
    private final VeiculoFrotaRepository veiculoFrotaRepository;

    public TransportadoraVeiculoFrotaService(TransportadoraVeiculoFrotaRepository repository,
                                             TransportadoraRepository transportadoraRepository,
                                             VeiculoFrotaRepository veiculoFrotaRepository) {
        this.repository = repository;
        this.transportadoraRepository = transportadoraRepository;
        this.veiculoFrotaRepository = veiculoFrotaRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransportadoraVeiculoFrota> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public TransportadoraVeiculoFrota buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("TransportadoraVeiculoFrota", id));
    }

    @Transactional
    public TransportadoraVeiculoFrota criar(TransportadoraVeiculoFrotaRequest req) {
        Transportadora transportadora = buscarTransportadora(req.transportadoraId());
        VeiculoFrota veiculo = buscarVeiculo(req.veiculoFrotaId());
        if (repository.existsByTransportadoraIdAndVeiculoFrotaId(transportadora.getId(), veiculo.getId())) {
            throw new ConflitoException("Este veiculo ja esta vinculado a esta transportadora.");
        }
        TransportadoraVeiculoFrota tvf = new TransportadoraVeiculoFrota();
        tvf.setTransportadora(transportadora);
        tvf.setVeiculoFrota(veiculo);
        return repository.save(tvf);
    }

    @Transactional
    public TransportadoraVeiculoFrota atualizar(Long id, TransportadoraVeiculoFrotaRequest req) {
        TransportadoraVeiculoFrota tvf = buscar(id);
        Transportadora transportadora = buscarTransportadora(req.transportadoraId());
        VeiculoFrota veiculo = buscarVeiculo(req.veiculoFrotaId());
        if (repository.existsByTransportadoraIdAndVeiculoFrotaIdAndIdNot(
                transportadora.getId(), veiculo.getId(), id)) {
            throw new ConflitoException("Este veiculo ja esta vinculado a esta transportadora.");
        }
        tvf.setTransportadora(transportadora);
        tvf.setVeiculoFrota(veiculo);
        return repository.save(tvf);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private Transportadora buscarTransportadora(Long id) {
        return transportadoraRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transportadora", id));
    }

    private VeiculoFrota buscarVeiculo(Long id) {
        return veiculoFrotaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("VeiculoFrota", id));
    }
}
