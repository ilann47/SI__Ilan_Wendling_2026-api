package com.kaneko.estacionamento.fiscal;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link NotaEntrada}. As consultas carregam os itens (e seus
 * produtos) e o fornecedor via {@link EntityGraph}.
 */
public interface NotaEntradaRepository extends JpaRepository<NotaEntrada, Long> {

    boolean existsByOrganizacaoIdAndNumeroAndSerieAndModeloAndFornecedorId(
            Long organizacaoId, String numero, String serie, String modelo, Long fornecedorId);

    @Query(value = "select n.id from NotaEntrada n where n.organizacaoId = :organizacaoId",
            countQuery = "select count(n.id) from NotaEntrada n where n.organizacaoId = :organizacaoId")
    Page<Long> findIdsByOrganizacaoId(@Param("organizacaoId") Long organizacaoId,
            Pageable pageable);

    @Query(value = "select n.id from NotaEntrada n where n.organizacaoId is null",
            countQuery = "select count(n.id) from NotaEntrada n where n.organizacaoId is null")
    Page<Long> findIdsByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "transportadora", "itens", "itens.produto"})
    List<NotaEntrada> findAllByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "transportadora", "itens", "itens.produto"})
    Optional<NotaEntrada> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "transportadora", "itens", "itens.produto"})
    Optional<NotaEntrada> findByIdAndOrganizacaoIdIsNull(Long id);

    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerieAndModeloAndFornecedorId(
            String numero, String serie, String modelo, Long fornecedorId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "localEstoque",
            "transportadora", "itens"})
    @org.springframework.data.jpa.repository.Query("select n from NotaEntrada n where n.id=:id and n.organizacaoId=:organizacaoId")
    Optional<NotaEntrada> findParaAtualizar(Long id, Long organizacaoId);
    @Query("""
        select n.id from NotaEntrada n
        where ((:org is null and n.organizacaoId is null) or n.organizacaoId=:org)
          and (:numero='' or locate(lower(:numero),lower(n.numero))>0)
          and (:situacao is null or n.situacao=:situacao)
          and (:fornecedorId is null or n.fornecedor.id=:fornecedorId)
        """)
    Page<Long> filtrarIds(Long org, String numero, SituacaoNota situacao, Long fornecedorId, Pageable pageable);

    @Query(value = """
        select count(*)>0 from nota_entrada n
        where n.organizacao_id is not distinct from :org and n.fornecedor_id=:fornecedor
          and normalizar_identificador_nota(n.numero)=:numero
          and normalizar_identificador_nota(n.serie)=:serie
          and normalizar_identificador_nota(n.modelo)=:modelo
          and (cast(:ignorar as bigint) is null or n.id<>:ignorar)
        """, nativeQuery=true)
    boolean existeIdentidade(Long org, Long fornecedor, String numero, String serie, String modelo, Long ignorar);

}
