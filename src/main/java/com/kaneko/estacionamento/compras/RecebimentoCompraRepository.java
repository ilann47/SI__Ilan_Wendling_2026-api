package com.kaneko.estacionamento.compras;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecebimentoCompraRepository extends JpaRepository<RecebimentoCompra, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RecebimentoCompra r where r.id=:id and r.organizacaoId=:organizacaoId")
    Optional<RecebimentoCompra> findParaAtualizar(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {
            "ordemCompra", "localEstoque", "ator", "itens", "itens.produto",
            "itens.itemOrdemCompra"})
    List<RecebimentoCompra> findAllByOrganizacaoIdAndOrdemCompraIdOrderByRecebidoEmDesc(
            Long organizacaoId, Long ordemCompraId);

    @EntityGraph(attributePaths = {
            "ordemCompra", "ordemCompra.fornecedor", "localEstoque", "ator",
            "itens", "itens.produto", "itens.itemOrdemCompra"})
    Optional<RecebimentoCompra> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
}
