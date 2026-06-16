package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.MarcaRequest;
import com.kaneko.estacionamento.conveniencia.dto.MarcaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Marca.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_marca_nome}) na criacao e na alteracao.
 */
@Service
public class MarcaService {

    private final MarcaRepository repository;

    public MarcaService(MarcaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<MarcaResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<Marca> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(MarcaResponse::from);
    }

    @Transactional(readOnly = true)
    public Marca buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marca", id));
    }

    @Transactional
    public Marca criar(MarcaRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe uma marca cadastrada com este nome.");
        }
        Marca marca = new Marca();
        aplicar(marca, req, nome);
        return repository.save(marca);
    }

    @Transactional
    public Marca atualizar(Long id, MarcaRequest req) {
        Marca marca = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe uma marca cadastrada com este nome.");
        }
        aplicar(marca, req, nome);
        return repository.save(marca);
    }

    @Transactional
    public void excluir(Long id) {
        Marca marca = buscar(id);
        repository.delete(marca);
    }

    private void aplicar(Marca marca, MarcaRequest req, String nome) {
        marca.setNome(nome);
        if (req.ativo() != null) {
            marca.setAtivo(req.ativo());
        }
    }
}
