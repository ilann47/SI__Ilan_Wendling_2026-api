package com.kaneko.estacionamento.instalacoes;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetorRepository extends JpaRepository<Setor, Long>, JpaSpecificationExecutor<Setor> {
    Optional<Setor> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query("select s.patioId from Setor s where s.id = :id and s.organizacaoId = :org")
    Optional<Long> buscarPatio(@Param("id") Long id, @Param("org") Long org);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Setor s where s.id = :id and s.organizacaoId = :org")
    Optional<Setor> buscarParaEdicao(@Param("id") Long id, @Param("org") Long org);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Setor s
            where s.id = :id and s.organizacaoId = :organizacaoId and s.ativo = true
            """)
    Optional<Setor> findAtivoByIdAndOrganizacaoIdForUpdate(
            @Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId);

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
