package com.kaneko.estacionamento.instalacoes;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatioInstalacaoRepository extends JpaRepository<PatioInstalacao, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from PatioInstalacao p
            where p.id = :id and p.organizacaoId = :organizacaoId and p.ativo = true
            """)
    Optional<PatioInstalacao> findAtivoByIdAndOrganizacaoIdForUpdate(
            @Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId);

    @Query("""
            select count(p) > 0 from PatioInstalacao p
            where p.organizacaoId = :organizacaoId
              and lower(trim(p.nome)) = lower(trim(:nome))
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome);
}
