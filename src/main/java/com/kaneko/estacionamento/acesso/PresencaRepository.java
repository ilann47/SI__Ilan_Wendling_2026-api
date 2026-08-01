package com.kaneko.estacionamento.acesso;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {
    Optional<Presenca> findByTentativaCheckinId(Long tentativaCheckinId);

    Optional<Presenca> findByTentativaCheckoutId(Long tentativaCheckoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Presenca p
            where p.organizacaoId = :organizacaoId
              and p.credencialId = :credencialId
              and p.status = com.kaneko.estacionamento.acesso.StatusPresenca.DENTRO
            """)
    Optional<Presenca> findAbertaForUpdate(
            @Param("organizacaoId") Long organizacaoId,
            @Param("credencialId") Long credencialId);
}
