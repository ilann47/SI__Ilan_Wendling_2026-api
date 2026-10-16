package com.kaneko.estacionamento.credencial;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepresentacaoQrRepository
        extends JpaRepository<RepresentacaoQr, Long> {
    Optional<RepresentacaoQr> findByOrganizacaoIdAndTokenHash(
            Long organizacaoId, String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from RepresentacaoQr r
            where r.organizacaoId = :organizacaoId and r.tokenHash = :tokenHash
            """)
    Optional<RepresentacaoQr> findByTokenHashForUpdate(
            @Param("organizacaoId") Long organizacaoId,
            @Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from RepresentacaoQr r
            where r.credencialId = :credencialId and r.revogadaEm is null
            """)
    Optional<RepresentacaoQr> findAtivaForUpdate(
            @Param("credencialId") Long credencialId);
}
