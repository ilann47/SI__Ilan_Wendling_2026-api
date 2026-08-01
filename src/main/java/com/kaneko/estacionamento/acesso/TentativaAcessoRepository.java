package com.kaneko.estacionamento.acesso;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TentativaAcessoRepository
        extends JpaRepository<TentativaAcesso, Long> {
    Optional<TentativaAcesso> findByIdAndOrganizacaoIdAndAtorId(
            Long id, Long organizacaoId, Long atorId);
}
