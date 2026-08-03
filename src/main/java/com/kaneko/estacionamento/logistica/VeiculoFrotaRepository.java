package com.kaneko.estacionamento.logistica;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio por tenant; espelha {@code uq_veiculo_frota_organizacao_placa}. */
public interface VeiculoFrotaRepository extends JpaRepository<VeiculoFrota, Long>,
        JpaSpecificationExecutor<VeiculoFrota> {

    Optional<VeiculoFrota> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query("""
            select count(v) > 0 from VeiculoFrota v
            where v.organizacaoId = :organizacaoId
              and upper(trim(v.placa)) = upper(trim(:placa))
              and (:idIgnorado is null or v.id <> :idIgnorado)
            """)
    boolean existePlaca(
            @Param("organizacaoId") Long organizacaoId,
            @Param("placa") String placa,
            @Param("idIgnorado") Long idIgnorado);
}
