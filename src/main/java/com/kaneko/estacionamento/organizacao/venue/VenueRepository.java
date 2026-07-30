package com.kaneko.estacionamento.organizacao.venue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    @Query("""
            select count(v) > 0 from Venue v
            where v.organizacaoId = :organizacaoId
              and lower(trim(v.nome)) = lower(trim(:nome))
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome);
}
