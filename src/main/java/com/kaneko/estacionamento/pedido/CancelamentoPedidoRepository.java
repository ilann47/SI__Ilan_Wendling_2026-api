package com.kaneko.estacionamento.pedido;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancelamentoPedidoRepository
        extends JpaRepository<CancelamentoPedido, Long> {
    @Query("""
            select c from CancelamentoPedido c join fetch c.pedido p
            where c.id = :id and c.organizacaoId = :organizacaoId
              and c.atorId = :atorId
            """)
    Optional<CancelamentoPedido> findDoAtor(
            @Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId,
            @Param("atorId") Long atorId);
}
