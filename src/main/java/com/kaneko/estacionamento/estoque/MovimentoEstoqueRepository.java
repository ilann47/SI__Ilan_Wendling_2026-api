package com.kaneko.estacionamento.estoque;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    Optional<MovimentoEstoque> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query(value = """
            SELECT m.id, m.produto_id AS produtoId, p.nome AS produto,
                   m.local_estoque_id AS localEstoqueId, l.nome AS localEstoque,
                   m.saldo_estoque_id AS saldoEstoqueId, m.tipo, m.delta,
                   m.saldo_anterior AS saldoAnterior, m.saldo_posterior AS saldoPosterior,
                   m.custo_unitario AS custoUnitario, m.origem_tipo AS origemTipo,
                   m.origem_chave AS origemChave, m.compensado_id AS compensadoId,
                   m.ator_id AS atorId, u.nome AS atorNome, m.motivo, m.ocorrido_em AS ocorridoEm,
                   m.created_at AS createdAt
            FROM movimento_estoque m
            JOIN produto p ON p.id=m.produto_id AND p.organizacao_id=m.organizacao_id
            JOIN local_estoque l ON l.id=m.local_estoque_id
                                 AND l.organizacao_id=m.organizacao_id
            LEFT JOIN usuario u ON u.id=m.ator_id
            WHERE m.id=:id AND m.organizacao_id=:organizacaoId
            """, nativeQuery = true)
    Optional<MovimentoEstoqueView> buscarView(Long id, Long organizacaoId);

    boolean existsByCompensadoId(Long compensadoId);

    @Query(value = """
            SELECT m.id, m.produto_id AS produtoId, p.nome AS produto,
                   m.local_estoque_id AS localEstoqueId, l.nome AS localEstoque,
                   m.saldo_estoque_id AS saldoEstoqueId, m.tipo, m.delta,
                   m.saldo_anterior AS saldoAnterior, m.saldo_posterior AS saldoPosterior,
                   m.custo_unitario AS custoUnitario, m.origem_tipo AS origemTipo,
                   m.origem_chave AS origemChave, m.compensado_id AS compensadoId,
                   m.ator_id AS atorId, u.nome AS atorNome, m.motivo, m.ocorrido_em AS ocorridoEm,
                   m.created_at AS createdAt
            FROM movimento_estoque m
            JOIN produto p ON p.id=m.produto_id AND p.organizacao_id=m.organizacao_id
            JOIN local_estoque l ON l.id=m.local_estoque_id
                                 AND l.organizacao_id=m.organizacao_id
            LEFT JOIN usuario u ON u.id=m.ator_id
            WHERE m.organizacao_id=:organizacaoId
              AND m.origem_tipo=:origemTipo
              AND m.origem_chave LIKE CONCAT(:origemPrefixo, '%')
            ORDER BY m.ocorrido_em ASC, m.id ASC
            """, nativeQuery = true)
    List<MovimentoEstoqueView> listarPorOrigem(
            Long organizacaoId, String origemTipo, String origemPrefixo);

    @Query(value = """
            SELECT m.id, m.produto_id AS produtoId, p.nome AS produto,
                   m.local_estoque_id AS localEstoqueId, l.nome AS localEstoque,
                   m.saldo_estoque_id AS saldoEstoqueId, m.tipo, m.delta,
                   m.saldo_anterior AS saldoAnterior, m.saldo_posterior AS saldoPosterior,
                   m.custo_unitario AS custoUnitario, m.origem_tipo AS origemTipo,
                   m.origem_chave AS origemChave, m.compensado_id AS compensadoId,
                   m.ator_id AS atorId, u.nome AS atorNome, m.motivo, m.ocorrido_em AS ocorridoEm,
                   m.created_at AS createdAt
            FROM movimento_estoque m
            JOIN produto p ON p.id=m.produto_id AND p.organizacao_id=m.organizacao_id
            JOIN local_estoque l ON l.id=m.local_estoque_id
                                 AND l.organizacao_id=m.organizacao_id
            LEFT JOIN usuario u ON u.id=m.ator_id
            WHERE m.organizacao_id=:organizacaoId AND m.id IN (:ids)
            ORDER BY m.ocorrido_em ASC, m.id ASC
            """, nativeQuery = true)
    List<MovimentoEstoqueView> listarPorIds(Long organizacaoId, Collection<Long> ids);

    @Query(value = """
            SELECT m.id, m.produto_id AS produtoId, p.nome AS produto,
                   m.local_estoque_id AS localEstoqueId, l.nome AS localEstoque,
                   m.saldo_estoque_id AS saldoEstoqueId, m.tipo, m.delta,
                   m.saldo_anterior AS saldoAnterior, m.saldo_posterior AS saldoPosterior,
                   m.custo_unitario AS custoUnitario, m.origem_tipo AS origemTipo,
                   m.origem_chave AS origemChave, m.compensado_id AS compensadoId,
                   m.ator_id AS atorId, u.nome AS atorNome, m.motivo, m.ocorrido_em AS ocorridoEm,
                   m.created_at AS createdAt
            FROM movimento_estoque m
            JOIN produto p ON p.id=m.produto_id AND p.organizacao_id=m.organizacao_id
            JOIN local_estoque l ON l.id=m.local_estoque_id
                                 AND l.organizacao_id=m.organizacao_id
            LEFT JOIN usuario u ON u.id=m.ator_id
            WHERE m.organizacao_id=:organizacaoId
              AND (:produtoId IS NULL OR m.produto_id=:produtoId)
              AND (:localId IS NULL OR m.local_estoque_id=:localId)
              AND (:tipo IS NULL OR m.tipo=:tipo)
              AND (CAST(:de AS timestamptz) IS NULL OR m.ocorrido_em>=CAST(:de AS timestamptz))
              AND (CAST(:ate AS timestamptz) IS NULL OR m.ocorrido_em<=CAST(:ate AS timestamptz))
            ORDER BY m.ocorrido_em DESC, m.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM movimento_estoque m
            WHERE m.organizacao_id=:organizacaoId
              AND (:produtoId IS NULL OR m.produto_id=:produtoId)
              AND (:localId IS NULL OR m.local_estoque_id=:localId)
              AND (:tipo IS NULL OR m.tipo=:tipo)
              AND (CAST(:de AS timestamptz) IS NULL OR m.ocorrido_em>=CAST(:de AS timestamptz))
              AND (CAST(:ate AS timestamptz) IS NULL OR m.ocorrido_em<=CAST(:ate AS timestamptz))
            """, nativeQuery = true)
    Page<MovimentoEstoqueView> listar(
            Long organizacaoId, Long produtoId, Long localId, String tipo,
            OffsetDateTime de, OffsetDateTime ate, Pageable pageable);
}
