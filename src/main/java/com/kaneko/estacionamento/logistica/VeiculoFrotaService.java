package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaRequest;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Veiculo de Frota: unicidade da placa
 * (normalizada em maiusculas).
 */
@Service
public class VeiculoFrotaService {

    private final VeiculoFrotaRepository repository;

    public VeiculoFrotaService(VeiculoFrotaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<VeiculoFrotaResponse> listar(String placa, String marca, Boolean ativo, Pageable pageable) {
        Specification<VeiculoFrota> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("placa", placa),
                FiltroSpecs.contemIgnoreCase("marca", marca),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(VeiculoFrotaResponse::from);
    }

    @Transactional(readOnly = true)
    public VeiculoFrota buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("VeiculoFrota", id));
    }

    @Transactional
    public VeiculoFrota criar(VeiculoFrotaRequest req) {
        String placa = normalizarPlaca(req.placa());
        if (repository.existsByPlaca(placa)) {
            throw new ConflitoException("Ja existe um veiculo de frota com esta placa.");
        }
        VeiculoFrota v = new VeiculoFrota();
        aplicar(v, req, placa);
        return repository.save(v);
    }

    @Transactional
    public VeiculoFrota atualizar(Long id, VeiculoFrotaRequest req) {
        VeiculoFrota v = buscar(id);
        String placa = normalizarPlaca(req.placa());
        if (repository.existsByPlacaAndIdNot(placa, id)) {
            throw new ConflitoException("Ja existe um veiculo de frota com esta placa.");
        }
        aplicar(v, req, placa);
        return repository.save(v);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(VeiculoFrota v, VeiculoFrotaRequest req, String placa) {
        v.setPlaca(placa);
        v.setModelo(req.modelo());
        v.setMarca(req.marca());
        v.setAno(req.ano());
        v.setCapacidade(req.capacidade());
        v.setObservacao(req.observacao());
        if (req.ativo() != null) {
            v.setAtivo(req.ativo());
        }
    }

    private String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase();
    }
}
