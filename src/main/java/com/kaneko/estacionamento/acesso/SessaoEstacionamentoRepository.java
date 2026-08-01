package com.kaneko.estacionamento.acesso;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoEstacionamentoRepository
        extends JpaRepository<SessaoEstacionamento, Long> {
    Optional<SessaoEstacionamento> findByTentativaEntradaId(
            Long tentativaEntradaId);

    Optional<SessaoEstacionamento> findByTentativaSaidaId(
            Long tentativaSaidaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SessaoEstacionamento s
            where s.organizacaoId = :organizacaoId
              and s.credencialId = :credencialId
              and s.status = com.kaneko.estacionamento.acesso.StatusSessaoEstacionamento.ABERTA
            """)
    Optional<SessaoEstacionamento> findAbertaForUpdate(
            @Param("organizacaoId") Long organizacaoId,
            @Param("credencialId") Long credencialId);
}
