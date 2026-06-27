package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.geografia.dto.PaisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Pais.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_pais_nome}) na criacao e na alteracao.
 */
@Service
public class PaisService {

    private final PaisRepository repository;

    public PaisService(PaisRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<PaisResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<Pais> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(PaisResponse::from);
    }

    @Transactional(readOnly = true)
    public Pais buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pais", id));
    }

    @Transactional
    public Pais criar(PaisRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe um pais cadastrado com este nome.");
        }
        Pais pais = new Pais();
        aplicar(pais, req, nome);
        return repository.save(pais);
    }

    @Transactional
    public Pais atualizar(Long id, PaisRequest req) {
        Pais pais = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe um pais cadastrado com este nome.");
        }
        aplicar(pais, req, nome);
        return repository.save(pais);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Pais pais, PaisRequest req, String nome) {
        pais.setNome(nome);
        pais.setSigla(req.sigla());
        pais.setCodigo(req.codigo());
        pais.setNacionalidade(req.nacionalidade());
        if (req.ativo() != null) {
            pais.setAtivo(req.ativo());
        }
    }
}
