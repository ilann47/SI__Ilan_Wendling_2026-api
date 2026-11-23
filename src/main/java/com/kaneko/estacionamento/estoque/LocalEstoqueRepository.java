package com.kaneko.estacionamento.estoque;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface LocalEstoqueRepository extends JpaRepository<LocalEstoque, Long> {

    Optional<LocalEstoque> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from LocalEstoque l
            where l.id=:id and l.organizacaoId=:organizacaoId
            """)
    Optional<LocalEstoque> findParaAtualizar(Long id, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select l from LocalEstoque l
            where l.id=:id and l.organizacaoId=:organizacaoId and l.ativo=true
            """)
    Optional<LocalEstoque> findAtivoParaMovimento(Long id, Long organizacaoId);

    Page<LocalEstoque> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);

    @Query("""
            select count(l)>0 from LocalEstoque l
            where l.organizacaoId=:organizacaoId
              and lower(trim(l.nome))=lower(trim(:nome))
              and (:excluirId is null or l.id<>:excluirId)
            """)
    boolean existeNomeNormalizado(
            Long organizacaoId, String nome, Long excluirId);
}
