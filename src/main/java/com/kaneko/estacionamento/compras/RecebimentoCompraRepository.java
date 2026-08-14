package com.kaneko.estacionamento.compras;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecebimentoCompraRepository extends JpaRepository<RecebimentoCompra, Long> {

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
