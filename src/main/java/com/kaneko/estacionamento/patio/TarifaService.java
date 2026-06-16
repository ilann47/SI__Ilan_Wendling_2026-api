package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.patio.dto.TarifaRequest;
import com.kaneko.estacionamento.patio.dto.TarifaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Tarifa.
 */
@Service
public class TarifaService {

    /** Tolerancia padrao (minutos) quando nao informada — espelha o DEFAULT do schema. */
    private static final int TOLERANCIA_PADRAO = 15;

    private final TarifaRepository repository;

    public TarifaService(TarifaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<TarifaResponse> listar(String descricao, Boolean ativo, Pageable pageable) {
        Specification<Tarifa> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("descricao", descricao),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(TarifaResponse::from);
    }

    @Transactional(readOnly = true)
    public Tarifa buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarifa", id));
    }

    @Transactional
    public Tarifa criar(TarifaRequest req) {
        Tarifa tarifa = new Tarifa();
        aplicar(tarifa, req);
        return repository.save(tarifa);
    }

    @Transactional
    public Tarifa atualizar(Long id, TarifaRequest req) {
        Tarifa tarifa = buscar(id);
        aplicar(tarifa, req);
        return repository.save(tarifa);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(Tarifa tarifa, TarifaRequest req) {
        tarifa.setDescricao(req.descricao().trim());
        tarifa.setValorHora(req.valorHora());
        tarifa.setValorDiaria(req.valorDiaria());
        tarifa.setToleranciaMin(req.toleranciaMin() != null ? req.toleranciaMin() : TOLERANCIA_PADRAO);
        tarifa.setValorMensalidade(req.valorMensalidade());
        if (req.ativo() != null) {
            tarifa.setAtivo(req.ativo());
        }
    }
}
