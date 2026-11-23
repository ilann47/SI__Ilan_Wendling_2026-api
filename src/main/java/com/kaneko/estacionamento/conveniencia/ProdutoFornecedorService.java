package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoFornecedorRequest;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras tenant-aware do vínculo Produto–Fornecedor. */
@Service
public class ProdutoFornecedorService {

    private final ProdutoFornecedorRepository repository;
    private final ProdutoRepository produtos;
    private final FornecedorRepository fornecedores;
    private final AuditoriaService auditoria;

    public ProdutoFornecedorService(ProdutoFornecedorRepository repository,
            ProdutoRepository produtos, FornecedorRepository fornecedores,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.produtos = produtos;
        this.fornecedores = fornecedores;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<ProdutoFornecedor> listar(
            Long organizacaoId, Long produtoId, Pageable pageable) {
        return produtoId == null
                ? repository.findAllByOrganizacaoId(organizacaoId, pageable)
                : repository.findAllByOrganizacaoIdAndProdutoId(
                        organizacaoId, produtoId, pageable);
    }

    @Transactional(readOnly = true)
    public ProdutoFornecedor buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "ProdutoFornecedor", id));
    }

    @Transactional
    public ProdutoFornecedor criar(Long organizacaoId, ProdutoFornecedorRequest request) {
        Produto produto = buscarProdutoAtivo(organizacaoId, request.produtoId());
        Fornecedor fornecedor = buscarFornecedorAtivo(
                organizacaoId, request.fornecedorId());
        var existente = repository.findByProdutoIdAndFornecedorIdAndOrganizacaoId(
                produto.getId(), fornecedor.getId(), organizacaoId);
        if (existente.isPresent()) {
            ProdutoFornecedor vinculo = existente.get();
            if (Boolean.TRUE.equals(vinculo.getAtivo())) {
                throw new ConflitoException(
                        "Este fornecedor ja esta vinculado a este produto.");
            }
            Map<String, Object> anterior = snapshot(vinculo);
            Long versaoAnterior = vinculo.getVersion();
            aplicarDados(vinculo, request);
            vinculo.setAtivo(true);
            vinculo = repository.saveAndFlush(vinculo);
            auditar(vinculo, "PRODUTO_FORNECEDOR_REATIVADO", anterior,
                    snapshot(vinculo), versaoAnterior, vinculo.getVersion());
            return vinculo;
        }

        ProdutoFornecedor vinculo = new ProdutoFornecedor();
        vinculo.setOrganizacaoId(organizacaoId);
        vinculo.setProduto(produto);
        vinculo.setFornecedor(fornecedor);
        aplicarDados(vinculo, request);
        vinculo = repository.saveAndFlush(vinculo);
        auditar(vinculo, "PRODUTO_FORNECEDOR_CRIADO", null,
                snapshot(vinculo), null, vinculo.getVersion());
        return vinculo;
    }

    @Transactional
    public ProdutoFornecedor atualizar(Long organizacaoId, Long id, long versaoEsperada,
            ProdutoFornecedorRequest request) {
        ProdutoFornecedor vinculo = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, vinculo.getVersion());
        if (!vinculo.getProduto().getId().equals(request.produtoId())
                || !vinculo.getFornecedor().getId().equals(request.fornecedorId())) {
            throw new ConflitoException(
                    "Produto e fornecedor definem a identidade do vinculo e nao podem ser alterados.");
        }
        boolean reativando = Boolean.FALSE.equals(vinculo.getAtivo())
                && Boolean.TRUE.equals(request.ativo());
        boolean ativoFinal = request.ativo() != null
                ? request.ativo() : Boolean.TRUE.equals(vinculo.getAtivo());
        if (ativoFinal) {
            buscarProdutoAtivo(organizacaoId, request.produtoId());
            buscarFornecedorAtivo(organizacaoId, request.fornecedorId());
        }
        Map<String, Object> anterior = snapshot(vinculo);
        Long versaoAnterior = vinculo.getVersion();
        aplicarDados(vinculo, request);
        if (request.ativo() != null) {
            vinculo.setAtivo(request.ativo());
        }
        vinculo = repository.saveAndFlush(vinculo);
        auditar(vinculo, reativando ? "PRODUTO_FORNECEDOR_REATIVADO"
                        : "PRODUTO_FORNECEDOR_ATUALIZADO", anterior,
                snapshot(vinculo), versaoAnterior, vinculo.getVersion());
        return vinculo;
    }

    @Transactional
    public ProdutoFornecedor excluir(Long organizacaoId, Long id, long versaoEsperada) {
        ProdutoFornecedor vinculo = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, vinculo.getVersion());
        Map<String, Object> anterior = snapshot(vinculo);
        Long versaoAnterior = vinculo.getVersion();
        vinculo.setAtivo(false);
        repository.flush();
        auditar(vinculo, "PRODUTO_FORNECEDOR_DESATIVADO", anterior,
                snapshot(vinculo), versaoAnterior, vinculo.getVersion());
        return vinculo;
    }

    private Produto buscarProdutoAtivo(Long organizacaoId, Long id) {
        Produto produto = produtos.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
        if (Boolean.FALSE.equals(produto.getAtivo())) {
            throw new ConflitoException("O produto esta inativo e nao pode ser vinculado.");
        }
        return produto;
    }

    private Fornecedor buscarFornecedorAtivo(Long organizacaoId, Long id) {
        Fornecedor fornecedor = fornecedores.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", id));
        if (Boolean.FALSE.equals(fornecedor.getAtivo())) {
            throw new ConflitoException(
                    "O fornecedor esta inativo e nao pode ser vinculado.");
        }
        return fornecedor;
    }

    private static void aplicarDados(
            ProdutoFornecedor vinculo, ProdutoFornecedorRequest request) {
        vinculo.setCodigoProd(normalizar(request.codigoProd()));
        vinculo.setCusto(request.custo());
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private static void conferirVersao(long esperada, Long atual) {
        if (atual == null || esperada != atual) {
            throw new VersaoCatalogoConvenienciaDivergenteException(
                    esperada, atual == null ? -1 : atual);
        }
    }

    private void auditar(ProdutoFornecedor vinculo, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior,
            Long versaoAnterior, Long versaoPosterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                vinculo.getOrganizacaoId(), acao, "PRODUTO_FORNECEDOR", vinculo.getId(),
                null, null, null, anterior, posterior, versaoAnterior, versaoPosterior,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static Map<String, Object> snapshot(ProdutoFornecedor vinculo) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("produtoId", vinculo.getProduto().getId());
        snapshot.put("fornecedorId", vinculo.getFornecedor().getId());
        snapshot.put("codigoProd", vinculo.getCodigoProd());
        snapshot.put("custo", vinculo.getCusto());
        snapshot.put("ativo", vinculo.getAtivo());
        return snapshot;
    }
}
