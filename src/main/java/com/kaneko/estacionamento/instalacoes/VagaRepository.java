package com.kaneko.estacionamento.instalacoes;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VagaRepository extends JpaRepository<Vaga, Long> {

    @Query("""
            select v from Vaga v
            where v.setorId = :setorId
              and upper(trim(v.codigo)) in :codigos
            """)
    List<Vaga> buscarPorCodigosNormalizados(
            @Param("setorId") Long setorId,
            @Param("codigos") Collection<String> codigos);

    long countBySetorId(Long setorId);
}
