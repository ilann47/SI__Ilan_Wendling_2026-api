package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<Categoria> listar(Pageable pageable) {
        return repository.findAll(pageable);
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
        Categoria categoria = buscar(id);
        repository.delete(categoria);
    }

    private void aplicar(Categoria categoria, CategoriaRequest req, String nome) {
        categoria.setNome(nome);
        categoria.setDescricao(req.descricao());
        if (req.ativo() != null) {
            categoria.setAtivo(req.ativo());
        }
    }
}
