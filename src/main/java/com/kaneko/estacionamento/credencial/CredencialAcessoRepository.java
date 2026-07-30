package com.kaneko.estacionamento.credencial;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredencialAcessoRepository
        extends JpaRepository<CredencialAcesso, Long> {
    Optional<CredencialAcesso> findByIdAndOrganizacaoIdAndAtorId(
            Long id, Long organizacaoId, Long atorId);

    long countByPedidoId(Long pedidoId);

    long countByItemPedidoId(Long itemPedidoId);
}
