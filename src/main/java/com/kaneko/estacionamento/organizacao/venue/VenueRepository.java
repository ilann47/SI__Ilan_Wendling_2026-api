package com.kaneko.estacionamento.organizacao.venue;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long>, JpaSpecificationExecutor<Venue> {

    Optional<Venue> findByIdAndOrganizacaoIdAndAtivoTrue(Long id, Long organizacaoId);

    Optional<Venue> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venue v where v.id = :id and v.organizacaoId = :organizacaoId")
    Optional<Venue> buscarParaEdicao(@Param("id") Long id, @Param("organizacaoId") Long organizacaoId);

    @Query("""
            select count(v) > 0 from Venue v
            where v.organizacaoId = :organizacaoId
              and lower(trim(v.nome)) = lower(trim(:nome))
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome);
}
