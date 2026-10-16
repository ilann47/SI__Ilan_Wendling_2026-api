package com.kaneko.estacionamento.fiscal.importacao;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ImportacaoNotaRepository extends JpaRepository<ImportacaoNotaEntrada,Long> {
    Optional<ImportacaoNotaEntrada> findByOrganizacaoIdAndChave(Long organizacaoId, String chave);
    @EntityGraph(attributePaths={"itens","itens.produto"})
    Optional<ImportacaoNotaEntrada> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImportacaoNotaEntrada i where i.id=:id and i.organizacaoId=:org")
    Optional<ImportacaoNotaEntrada> bloquear(Long id, Long org);
    @Query("""
        select new com.kaneko.estacionamento.fiscal.importacao.ResumoImportacaoResponse(
            i.id,i.version,i.status,i.chave,i.numero,i.emitenteNome,i.total,i.createdAt,i.notaEntradaId)
        from ImportacaoNotaEntrada i where i.organizacaoId=:org
        """)
    Page<ResumoImportacaoResponse> listar(Long org, Pageable pageable);
}
