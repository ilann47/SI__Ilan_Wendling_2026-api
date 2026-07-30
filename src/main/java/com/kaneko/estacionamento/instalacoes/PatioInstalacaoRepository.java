package com.kaneko.estacionamento.instalacoes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatioInstalacaoRepository extends JpaRepository<PatioInstalacao, Long> {

    @Query("""
            select count(p) > 0 from PatioInstalacao p
            where p.organizacaoId = :organizacaoId
              and lower(trim(p.nome)) = lower(trim(:nome))
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome);
}
