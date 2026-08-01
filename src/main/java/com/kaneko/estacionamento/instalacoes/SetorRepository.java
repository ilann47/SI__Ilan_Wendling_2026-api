package com.kaneko.estacionamento.instalacoes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetorRepository extends JpaRepository<Setor, Long> {

    @Query("""
            select count(s) > 0 from Setor s
            where s.patioId = :patioId
              and lower(trim(s.codigo)) = lower(trim(:codigo))
            """)
    boolean existeCodigoNormalizado(
            @Param("patioId") Long patioId,
            @Param("codigo") String codigo);

    @Query("select coalesce(sum(s.capacidade), 0) from Setor s where s.patioId = :patioId")
    long somarCapacidadePorPatio(@Param("patioId") Long patioId);
}
