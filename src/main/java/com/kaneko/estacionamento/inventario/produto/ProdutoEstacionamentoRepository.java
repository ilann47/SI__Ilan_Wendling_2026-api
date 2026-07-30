package com.kaneko.estacionamento.inventario.produto;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoEstacionamentoRepository
        extends JpaRepository<ProdutoEstacionamento, Long> {

    Optional<ProdutoEstacionamento> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    boolean existsByEventoIdAndOrganizacaoIdAndStatus(
            Long eventoId, Long organizacaoId, StatusProdutoEstacionamento status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from ProdutoEstacionamento p
            where p.id = :id and p.organizacaoId = :organizacaoId
            """)
    Optional<ProdutoEstacionamento> findByIdAndOrganizacaoIdForUpdate(
            @Param("id") Long id, @Param("organizacaoId") Long organizacaoId);

    @Query("""
            select count(p) > 0 from ProdutoEstacionamento p
            where p.eventoId = :eventoId
              and lower(trim(p.nome)) = lower(trim(:nome))
            """)
    boolean existeNomeNormalizado(
            @Param("eventoId") Long eventoId,
            @Param("nome") String nome);

    @Query("""
            select coalesce(sum(p.quota), 0) from ProdutoEstacionamento p
            where p.alocacaoId = :alocacaoId
            """)
    Long somarQuotasPorAlocacao(@Param("alocacaoId") Long alocacaoId);
}
