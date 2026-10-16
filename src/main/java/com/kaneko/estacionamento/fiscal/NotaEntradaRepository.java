package com.kaneko.estacionamento.fiscal;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link NotaEntrada}. As consultas carregam os itens (e seus
 * produtos) e o fornecedor via {@link EntityGraph}.
 */
public interface NotaEntradaRepository extends JpaRepository<NotaEntrada, Long> {

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "recebimentoCompra", "recebimentoCompra.ordemCompra", "itens", "itens.produto"})
    List<NotaEntrada> findByOrganizacaoIdAndRecebimentoCompraOrdemCompraIdOrderByIdAsc(
            Long organizacaoId, Long ordemCompraId);

    boolean existsByOrganizacaoIdAndNumeroAndSerieAndModeloAndFornecedorId(
            Long organizacaoId, String numero, String serie, String modelo, Long fornecedorId);

    boolean existsByOrganizacaoIdAndRecebimentoCompraId(
            Long organizacaoId, Long recebimentoCompraId);

    @Query(value = "select n.id from NotaEntrada n where n.organizacaoId = :organizacaoId",
            countQuery = "select count(n.id) from NotaEntrada n where n.organizacaoId = :organizacaoId")
    Page<Long> findIdsByOrganizacaoId(@Param("organizacaoId") Long organizacaoId,
            Pageable pageable);

    @Query(value = "select n.id from NotaEntrada n where n.organizacaoId is null",
            countQuery = "select count(n.id) from NotaEntrada n where n.organizacaoId is null")
    Page<Long> findIdsByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "recebimentoCompra", "recebimentoCompra.ordemCompra", "itens", "itens.produto"})
    List<NotaEntrada> findAllByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "recebimentoCompra", "recebimentoCompra.ordemCompra", "itens", "itens.produto"})
    Optional<NotaEntrada> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "recebimentoCompra", "recebimentoCompra.ordemCompra", "itens", "itens.produto"})
    Optional<NotaEntrada> findByIdAndOrganizacaoIdIsNull(Long id);

    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerieAndModeloAndFornecedorId(
            String numero, String serie, String modelo, Long fornecedorId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "recebimentoCompra", "recebimentoCompra.ordemCompra", "itens", "itens.produto"})
    @org.springframework.data.jpa.repository.Query("select n from NotaEntrada n where n.id=:id and n.organizacaoId=:organizacaoId")
    Optional<NotaEntrada> findParaAtualizar(Long id, Long organizacaoId);
}
