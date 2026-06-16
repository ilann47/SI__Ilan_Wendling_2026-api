package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.rh.dto.CargoRequest;
import com.kaneko.estacionamento.rh.dto.CargoResponse;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Cargo (unicidade do nome).
 */
@Service
public class CargoService {

    private final CargoRepository repository;

    public CargoService(CargoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<CargoResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<Cargo> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(CargoResponse::from);
    }

    @Transactional(readOnly = true)
    public Cargo buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cargo", id));
    }

    @Transactional
    public Cargo criar(CargoRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe um cargo cadastrado com este nome.");
        }
        Cargo cargo = new Cargo();
        aplicar(cargo, req, nome);
        return repository.save(cargo);
    }

    @Transactional
    public Cargo atualizar(Long id, CargoRequest req) {
        Cargo cargo = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe um cargo cadastrado com este nome.");
        }
        aplicar(cargo, req, nome);
        return repository.save(cargo);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(Cargo cargo, CargoRequest req, String nome) {
        cargo.setNome(nome);
        cargo.setDescricao(req.descricao());
        cargo.setSalarioBase(req.salarioBase() != null ? req.salarioBase() : BigDecimal.ZERO);
        cargo.setCargaHoraria(req.cargaHoraria());
        cargo.setRequerCnh(req.requerCnh() != null ? req.requerCnh() : Boolean.FALSE);
        if (req.ativo() != null) {
            cargo.setAtivo(req.ativo());
        }
    }
}
