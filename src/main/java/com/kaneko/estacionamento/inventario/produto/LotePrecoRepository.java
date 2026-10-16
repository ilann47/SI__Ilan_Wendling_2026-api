package com.kaneko.estacionamento.inventario.produto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotePrecoRepository extends JpaRepository<LotePreco, Long> {

    boolean existsByProdutoId(Long produtoId);

    Optional<LotePreco> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    List<LotePreco> findAllByOrganizacaoIdAndProdutoIdOrderByPrioridadeDescIdAsc(
            Long organizacaoId, Long produtoId);

    @Query("""
            select l from LotePreco l
            where l.produtoId in :produtoIds
              and l.inicioVenda <= :instante
              and l.fimVenda > :instante
            order by l.produtoId, l.prioridade desc, l.id
            """)
    List<LotePreco> buscarVigentesOrdenadosPorPrioridade(
            @Param("produtoIds") List<Long> produtoIds,
            @Param("instante") OffsetDateTime instante);

    @Query("""
            select count(l) > 0 from LotePreco l
            where l.produtoId = :produtoId
              and lower(trim(l.nome)) = lower(trim(:nome))
            """)
    boolean existeNomeNormalizado(
            @Param("produtoId") Long produtoId, @Param("nome") String nome);

    @Query("""
            select count(l) > 0 from LotePreco l
            where l.produtoId = :produtoId
              and l.prioridade = :prioridade
              and l.inicioVenda < :fim
              and l.fimVenda > :inicio
            """)
    boolean existeSobreposicaoAmbigua(
            @Param("produtoId") Long produtoId,
            @Param("prioridade") Integer prioridade,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);
}
