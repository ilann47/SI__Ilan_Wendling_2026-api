package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.instalacoes.dto.VagaResumo;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VagaRepository extends JpaRepository<Vaga, Long> {

    @Query(value = """
            select new com.kaneko.estacionamento.instalacoes.dto.VagaResumo(
                v.id, v.codigo, s.id, s.nome, p.nome, v.tipo, v.acessivel, v.posicao, v.status, v.version)
            from Vaga v
            join Setor s on s.id = v.setorId and s.organizacaoId = v.organizacaoId
            join PatioInstalacao p on p.id = s.patioId and p.organizacaoId = v.organizacaoId
            where v.organizacaoId = :org and v.setorId = coalesce(:sectorId, v.setorId)
              and locate(:code, upper(v.codigo)) > 0
            """, countQuery = """
            select count(v) from Vaga v
            where v.organizacaoId = :org and v.setorId = coalesce(:sectorId, v.setorId)
              and locate(:code, upper(v.codigo)) > 0
            """)
    Page<VagaResumo> listar(@Param("org") Long org, @Param("sectorId") Long sectorId,
            @Param("code") String code, Pageable page);

    Optional<Vaga> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query("select v.setorId from Vaga v where v.id = :id and v.organizacaoId = :org")
    Optional<Long> buscarSetor(@Param("id") Long id, @Param("org") Long org);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vaga v where v.id = :id and v.organizacaoId = :org")
    Optional<Vaga> buscarParaEdicao(@Param("id") Long id, @Param("org") Long org);

    @Query("""
            select v from Vaga v
            where v.setorId = :setorId
              and upper(trim(v.codigo)) in :codigos
            """)
    List<Vaga> buscarPorCodigosNormalizados(
            @Param("setorId") Long setorId,
            @Param("codigos") Collection<String> codigos);

    long countBySetorId(Long setorId);

    @Query("select distinct v.tipo from Vaga v where v.setorId = :setorId")
    List<CategoriaSetor> buscarCategoriasEmUso(@Param("setorId") Long setorId);
}
