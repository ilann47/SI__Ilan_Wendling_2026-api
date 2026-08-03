package com.kaneko.estacionamento.pagamento;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link CondicaoPagamento}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega as {@code parcelas}
 * (e a forma de pagamento de cada parcela) via {@link EntityGraph} para permitir
 * a montagem da resposta fora da transacao sem lazy loading.
 */
public interface CondicaoPagamentoRepository extends JpaRepository<CondicaoPagamento, Long>,
        JpaSpecificationExecutor<CondicaoPagamento> {

    @EntityGraph(attributePaths = {"parcelas", "parcelas.formaPagamento"})
    Optional<CondicaoPagamento> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"parcelas", "parcelas.formaPagamento"})
    Optional<CondicaoPagamento> findByIdAndOrganizacaoIdIsNull(Long id);

    @Override
    @EntityGraph(attributePaths = {"parcelas", "parcelas.formaPagamento"})
    Optional<CondicaoPagamento> findById(Long id);

    @Query("""
            select count(c) > 0 from CondicaoPagamento c
            where c.organizacaoId = :organizacaoId
              and lower(trim(c.nome)) = lower(trim(:nome))
              and (:idIgnorado is null or c.id <> :idIgnorado)
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome,
            @Param("idIgnorado") Long idIgnorado);
}
