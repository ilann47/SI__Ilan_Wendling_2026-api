package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface SaldoEstoqueRepository extends JpaRepository<SaldoEstoque, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO saldo_estoque
                (organizacao_id,produto_id,local_estoque_id,quantidade,version,created_at,updated_at)
            VALUES (:organizacaoId,:produtoId,:localId,0,0,now(),now())
            ON CONFLICT (organizacao_id,produto_id,local_estoque_id) DO NOTHING
            """, nativeQuery = true)
    int garantirCarrier(Long organizacaoId, Long produtoId, Long localId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SaldoEstoque s
            where s.organizacaoId=:organizacaoId and s.produtoId=:produtoId
              and s.localEstoqueId=:localId
            """)
    Optional<SaldoEstoque> buscarParaAtualizar(
            Long organizacaoId, Long produtoId, Long localId);

    @Query("""
            select coalesce(sum(s.quantidade),0) from SaldoEstoque s
            where s.organizacaoId=:organizacaoId and s.produtoId=:produtoId
            """)
    BigDecimal somarPorProduto(Long organizacaoId, Long produtoId);

    @Query("""
            select count(s)>0 from SaldoEstoque s
            where s.organizacaoId=:organizacaoId and s.localEstoqueId=:localId
              and s.quantidade<>0
            """)
    boolean possuiSaldoNaoZero(Long organizacaoId, Long localId);

    @Query(value = """
            SELECT s.id, s.produto_id AS produtoId, p.nome AS produto,
                   s.local_estoque_id AS localEstoqueId, l.nome AS localEstoque,
                   s.quantidade, s.version
            FROM saldo_estoque s
            JOIN produto p ON p.id=s.produto_id AND p.organizacao_id=s.organizacao_id
            JOIN local_estoque l ON l.id=s.local_estoque_id
                                 AND l.organizacao_id=s.organizacao_id
            WHERE s.organizacao_id=:organizacaoId
              AND (:produtoId IS NULL OR s.produto_id=:produtoId)
              AND (:localId IS NULL OR s.local_estoque_id=:localId)
            """,
            countQuery = """
            SELECT count(*) FROM saldo_estoque s
            WHERE s.organizacao_id=:organizacaoId
              AND (:produtoId IS NULL OR s.produto_id=:produtoId)
              AND (:localId IS NULL OR s.local_estoque_id=:localId)
            """, nativeQuery = true)
    Page<SaldoEstoqueView> listarSaldos(
            Long organizacaoId, Long produtoId, Long localId, Pageable pageable);

    @Query(value = """
            WITH posicao AS (
                SELECT p.id, p.nome, p.quantidade_minima,
                       COALESCE(sum(s.quantidade),0) AS quantidade
                FROM produto p
                LEFT JOIN saldo_estoque s
                  ON s.organizacao_id=p.organizacao_id AND s.produto_id=p.id
                WHERE p.organizacao_id=:organizacaoId AND p.ativo=true
                  AND (:produtoId IS NULL OR p.id=:produtoId)
                GROUP BY p.id, p.nome, p.quantidade_minima
            )
            SELECT id AS produtoId, nome AS produto, quantidade,
                   quantidade_minima AS quantidadeMinima,
                   (quantidade <= quantidade_minima) AS abaixoMinimo
            FROM posicao
            WHERE (CAST(:abaixoMinimo AS boolean) IS NULL
                   OR (quantidade <= quantidade_minima)=CAST(:abaixoMinimo AS boolean))
            """,
            countQuery = """
            WITH posicao AS (
                SELECT p.id, p.quantidade_minima,
                       COALESCE(sum(s.quantidade),0) AS quantidade
                FROM produto p
                LEFT JOIN saldo_estoque s
                  ON s.organizacao_id=p.organizacao_id AND s.produto_id=p.id
                WHERE p.organizacao_id=:organizacaoId AND p.ativo=true
                  AND (:produtoId IS NULL OR p.id=:produtoId)
                GROUP BY p.id, p.quantidade_minima
            )
            SELECT count(*) FROM posicao
            WHERE (CAST(:abaixoMinimo AS boolean) IS NULL
                   OR (quantidade <= quantidade_minima)=CAST(:abaixoMinimo AS boolean))
            """, nativeQuery = true)
    Page<PosicaoEstoqueView> listarPosicoes(
            Long organizacaoId, Long produtoId, Boolean abaixoMinimo, Pageable pageable);
}
