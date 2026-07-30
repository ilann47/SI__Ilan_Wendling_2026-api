package com.kaneko.estacionamento.pedido;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmacaoManualPedidoRepository
        extends JpaRepository<ConfirmacaoManualPedido, Long> {
    Optional<ConfirmacaoManualPedido> findByPedidoIdAndOrganizacaoId(
            Long pedidoId, Long organizacaoId);
}
