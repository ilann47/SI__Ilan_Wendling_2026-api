package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Estado.
 *
 * <p>Valida a existencia do pais referenciado e a unicidade da UF dentro do
 * pais (espelhando {@code uq_estado_uf_pais}). A UF e normalizada para
 * maiusculas.
 */
@Service
public class EstadoService {

    private final EstadoRepository repository;
    private final PaisRepository paisRepository;

    public EstadoService(EstadoRepository repository, PaisRepository paisRepository) {
        this.repository = repository;
        this.paisRepository = paisRepository;
    }

    @Transactional(readOnly = true)
    public Page<EstadoResponse> listar(Long paisId, String uf, Boolean ativo, Pageable pageable) {
        Specification<Estado> filtro = FiltroSpecs.todos(
                FiltroSpecs.fkId("pais", paisId),
                FiltroSpecs.contemIgnoreCase("uf", uf),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(EstadoResponse::from);
    }

    @Transactional(readOnly = true)
    public Estado buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estado", id));
    }

    @Transactional
    public Estado criar(EstadoRequest req) {
        Pais pais = buscarPais(req.paisId());
        String uf = req.uf().trim().toUpperCase();
        if (repository.existsByPaisIdAndUf(pais.getId(), uf)) {
            throw new ConflitoException("Ja existe um estado cadastrado com esta UF para o pais selecionado.");
        }
        Estado estado = new Estado();
        estado.setPais(pais);
        aplicar(estado, req, uf);
        return repository.save(estado);
    }

    @Transactional
    public Estado atualizar(Long id, EstadoRequest req) {
        Estado estado = buscar(id);
        Pais pais = buscarPais(req.paisId());
        String uf = req.uf().trim().toUpperCase();
        if (repository.existsByPaisIdAndUfAndIdNot(pais.getId(), uf, id)) {
            throw new ConflitoException("Ja existe um estado cadastrado com esta UF para o pais selecionado.");
        }
        estado.setPais(pais);
        aplicar(estado, req, uf);
        return repository.save(estado);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private Pais buscarPais(Long paisId) {
        return paisRepository.findById(paisId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pais", paisId));
    }

    private void aplicar(Estado estado, EstadoRequest req, String uf) {
        estado.setNome(req.nome().trim());
        estado.setUf(uf);
        if (req.ativo() != null) {
            estado.setAtivo(req.ativo());
        }
    }
}
