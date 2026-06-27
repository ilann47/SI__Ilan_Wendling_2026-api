package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaRequest;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Categoria.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_categoria_nome}) na criacao e na alteracao.
 */
@Service
public class CategoriaService {

    private final CategoriaRepository repository;

    public CategoriaService(CategoriaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<Categoria> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(CategoriaResponse::from);
    }

    @Transactional(readOnly = true)
    public Categoria buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria", id));
    }

    @Transactional
    public Categoria criar(CategoriaRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe uma categoria cadastrada com este nome.");
        }
        Categoria categoria = new Categoria();
        aplicar(categoria, req, nome);
        return repository.save(categoria);
    }

    @Transactional
    public Categoria atualizar(Long id, CategoriaRequest req) {
        Categoria categoria = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe uma categoria cadastrada com este nome.");
        }
        aplicar(categoria, req, nome);
        return repository.save(categoria);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Categoria categoria, CategoriaRequest req, String nome) {
        categoria.setNome(nome);
        categoria.setDescricao(req.descricao());
        if (req.ativo() != null) {
            categoria.setAtivo(req.ativo());
        }
    }
}
