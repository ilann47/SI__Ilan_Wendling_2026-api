package com.kaneko.estacionamento.fiscal;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link NotaEntrada}. As consultas carregam os itens (e seus
 * produtos) e o fornecedor via {@link EntityGraph}.
 */
public interface NotaEntradaRepository extends JpaRepository<NotaEntrada, Long> {

    boolean existsByOrganizacaoIdAndNumeroAndSerieAndFornecedorId(
            Long organizacaoId, String numero, String serie, Long fornecedorId);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Page<NotaEntrada> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Page<NotaEntrada> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Optional<NotaEntrada> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Optional<NotaEntrada> findByIdAndOrganizacaoIdIsNull(Long id);

    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerieAndFornecedorId(
            String numero, String serie, Long fornecedorId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from NotaEntrada n where n.id=:id and n.organizacaoId=:organizacaoId")
    Optional<NotaEntrada> findParaAtualizar(Long id, Long organizacaoId);
}
