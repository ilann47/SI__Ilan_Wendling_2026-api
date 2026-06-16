package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoRequest;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Produto.
 *
 * <p>Exige marca e unidade de medida existentes; valida a categoria opcional e
 * a unicidade do codigo de barras (quando informado).
 */
@Service
public class ProdutoService {

    private final ProdutoRepository repository;
    private final MarcaRepository marcaRepository;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final CategoriaRepository categoriaRepository;

    public ProdutoService(ProdutoRepository repository,
                          MarcaRepository marcaRepository,
                          UnidadeMedidaRepository unidadeMedidaRepository,
                          CategoriaRepository categoriaRepository) {
        this.repository = repository;
        this.marcaRepository = marcaRepository;
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public Page<Produto> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Produto buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
    }

    @Transactional
    public Produto criar(ProdutoRequest req) {
        String codigoBarras = normalizarCodigoBarras(req.codigoBarras());
        if (codigoBarras != null && repository.existsByCodigoBarras(codigoBarras)) {
            throw new ConflitoException("Ja existe um produto com este codigo de barras.");
        }
        Produto produto = new Produto();
        aplicar(produto, req, codigoBarras);
        return repository.save(produto);
    }

    @Transactional
    public Produto atualizar(Long id, ProdutoRequest req) {
        Produto produto = buscar(id);
        String codigoBarras = normalizarCodigoBarras(req.codigoBarras());
        if (codigoBarras != null && repository.existsByCodigoBarrasAndIdNot(codigoBarras, id)) {
            throw new ConflitoException("Ja existe um produto com este codigo de barras.");
        }
        aplicar(produto, req, codigoBarras);
        return repository.save(produto);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(Produto p, ProdutoRequest req, String codigoBarras) {
        p.setNome(req.nome().trim());
        p.setCodigoBarras(codigoBarras);
        p.setReferencia(req.referencia());
        p.setMarca(buscarMarca(req.marcaId()));
        p.setUnidadeMedida(buscarUnidade(req.unidadeMedidaId()));
        p.setCategoria(req.categoriaId() != null ? buscarCategoria(req.categoriaId()) : null);
        p.setValorCompra(req.valorCompra() != null ? req.valorCompra() : BigDecimal.ZERO);
        p.setValorVenda(req.valorVenda() != null ? req.valorVenda() : BigDecimal.ZERO);
        p.setPercentualLucro(req.percentualLucro() != null ? req.percentualLucro() : BigDecimal.ZERO);
        p.setQuantidade(req.quantidade() != null ? req.quantidade() : BigDecimal.ZERO);
        p.setQuantidadeMinima(req.quantidadeMinima() != null ? req.quantidadeMinima() : BigDecimal.ONE);
        p.setDescricao(req.descricao());
        p.setObservacao(req.observacao());
        if (req.ativo() != null) {
            p.setAtivo(req.ativo());
        }
    }

    private String normalizarCodigoBarras(String codigoBarras) {
        if (codigoBarras == null) {
            return null;
        }
        String limpo = codigoBarras.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    private Marca buscarMarca(Long id) {
        return marcaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marca", id));
    }

    private UnidadeMedida buscarUnidade(Long id) {
        return unidadeMedidaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("UnidadeMedida", id));
    }

    private Categoria buscarCategoria(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria", id));
    }
}
