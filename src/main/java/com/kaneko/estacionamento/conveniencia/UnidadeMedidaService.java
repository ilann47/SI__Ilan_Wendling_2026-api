package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.UnidadeMedidaRequest;
import com.kaneko.estacionamento.conveniencia.dto.UnidadeMedidaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de UnidadeMedida.
 *
 * <p>Garante a unicidade da sigla (RN do cadastro, espelhando a restricao
 * {@code uq_unidade_sigla}) na criacao e na alteracao. A sigla e normalizada
 * para maiusculas antes da validacao e da persistencia.
 */
@Service
public class UnidadeMedidaService {

    private final UnidadeMedidaRepository repository;

    public UnidadeMedidaService(UnidadeMedidaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<UnidadeMedidaResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<UnidadeMedida> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(UnidadeMedidaResponse::from);
    }

    @Transactional(readOnly = true)
    public UnidadeMedida buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("UnidadeMedida", id));
    }

    @Transactional
    public UnidadeMedida criar(UnidadeMedidaRequest req) {
        String sigla = req.sigla().trim().toUpperCase();
        if (repository.existsBySigla(sigla)) {
            throw new ConflitoException("Ja existe uma unidade de medida cadastrada com esta sigla.");
        }
        UnidadeMedida unidade = new UnidadeMedida();
        aplicar(unidade, req, sigla);
        return repository.save(unidade);
    }

    @Transactional
    public UnidadeMedida atualizar(Long id, UnidadeMedidaRequest req) {
        UnidadeMedida unidade = buscar(id);
        String sigla = req.sigla().trim().toUpperCase();
        if (repository.existsBySiglaAndIdNot(sigla, id)) {
            throw new ConflitoException("Ja existe uma unidade de medida cadastrada com esta sigla.");
        }
        aplicar(unidade, req, sigla);
        return repository.save(unidade);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(UnidadeMedida unidade, UnidadeMedidaRequest req, String sigla) {
        unidade.setNome(req.nome().trim());
        unidade.setSigla(sigla);
        if (req.ativo() != null) {
            unidade.setAtivo(req.ativo());
        }
    }
}
