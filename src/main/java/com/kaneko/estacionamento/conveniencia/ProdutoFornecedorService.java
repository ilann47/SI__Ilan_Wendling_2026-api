package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoFornecedorRequest;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do vinculo produto-fornecedor (existencia das pontas e
 * unicidade do par).
 */
@Service
public class ProdutoFornecedorService {

    private final ProdutoFornecedorRepository repository;
    private final ProdutoRepository produtoRepository;
    private final FornecedorRepository fornecedorRepository;

    public ProdutoFornecedorService(ProdutoFornecedorRepository repository,
                                    ProdutoRepository produtoRepository,
                                    FornecedorRepository fornecedorRepository) {
        this.repository = repository;
        this.produtoRepository = produtoRepository;
        this.fornecedorRepository = fornecedorRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProdutoFornecedor> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ProdutoFornecedor buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ProdutoFornecedor", id));
    }

    @Transactional
    public ProdutoFornecedor criar(ProdutoFornecedorRequest req) {
        Produto produto = buscarProduto(req.produtoId());
        Fornecedor fornecedor = buscarFornecedor(req.fornecedorId());
        if (repository.existsByProdutoIdAndFornecedorId(produto.getId(), fornecedor.getId())) {
            throw new ConflitoException("Este fornecedor ja esta vinculado a este produto.");
        }
        ProdutoFornecedor pf = new ProdutoFornecedor();
        pf.setProduto(produto);
        pf.setFornecedor(fornecedor);
        aplicar(pf, req);
        return repository.save(pf);
    }

    @Transactional
    public ProdutoFornecedor atualizar(Long id, ProdutoFornecedorRequest req) {
        ProdutoFornecedor pf = buscar(id);
        Produto produto = buscarProduto(req.produtoId());
        Fornecedor fornecedor = buscarFornecedor(req.fornecedorId());
        if (repository.existsByProdutoIdAndFornecedorIdAndIdNot(produto.getId(), fornecedor.getId(), id)) {
            throw new ConflitoException("Este fornecedor ja esta vinculado a este produto.");
        }
        pf.setProduto(produto);
        pf.setFornecedor(fornecedor);
        aplicar(pf, req);
        return repository.save(pf);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(ProdutoFornecedor pf, ProdutoFornecedorRequest req) {
        pf.setCodigoProd(req.codigoProd());
        pf.setCusto(req.custo());
    }

    private Produto buscarProduto(Long id) {
        return produtoRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
    }

    private Fornecedor buscarFornecedor(Long id) {
        return fornecedorRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", id));
    }
}
