package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.CidadeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Cidade.
 *
 * <p>Valida a existencia do estado referenciado e a unicidade do nome dentro
 * do estado (espelhando {@code uq_cidade_nome_estado}).
 */
@Service
public class CidadeService {

    private final CidadeRepository repository;
    private final EstadoRepository estadoRepository;

    public CidadeService(CidadeRepository repository, EstadoRepository estadoRepository) {
        this.repository = repository;
        this.estadoRepository = estadoRepository;
    }

    @Transactional(readOnly = true)
    public Page<CidadeResponse> listar(Long estadoId, String nome, Pageable pageable) {
        Specification<Cidade> filtro = FiltroSpecs.todos(
                FiltroSpecs.fkId("estado", estadoId),
                FiltroSpecs.contemIgnoreCase("nome", nome));
        return repository.findAll(filtro, pageable).map(CidadeResponse::from);
    }

    @Transactional(readOnly = true)
    public Cidade buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", id));
    }

    @Transactional
    public Cidade criar(CidadeRequest req) {
        Estado estado = buscarEstado(req.estadoId());
        String nome = req.nome().trim();
        if (repository.existsByEstadoIdAndNome(estado.getId(), nome)) {
            throw new ConflitoException("Ja existe uma cidade cadastrada com este nome para o estado selecionado.");
        }
        Cidade cidade = new Cidade();
        cidade.setEstado(estado);
        aplicar(cidade, req, nome);
        return repository.save(cidade);
    }

    @Transactional
    public Cidade atualizar(Long id, CidadeRequest req) {
        Cidade cidade = buscar(id);
        Estado estado = buscarEstado(req.estadoId());
        String nome = req.nome().trim();
        if (repository.existsByEstadoIdAndNomeAndIdNot(estado.getId(), nome, id)) {
            throw new ConflitoException("Ja existe uma cidade cadastrada com este nome para o estado selecionado.");
        }
        cidade.setEstado(estado);
        aplicar(cidade, req, nome);
        return repository.save(cidade);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private Estado buscarEstado(Long estadoId) {
        return estadoRepository.findById(estadoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estado", estadoId));
    }

    private void aplicar(Cidade cidade, CidadeRequest req, String nome) {
        cidade.setNome(nome);
        cidade.setDdd(req.ddd());
        cidade.setCodigoIbge(req.codigoIbge());
        if (req.ativo() != null) {
            cidade.setAtivo(req.ativo());
        }
    }
}
