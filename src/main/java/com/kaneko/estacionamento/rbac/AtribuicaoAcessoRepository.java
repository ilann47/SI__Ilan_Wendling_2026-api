package com.kaneko.estacionamento.rbac;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtribuicaoAcessoRepository extends JpaRepository<AtribuicaoAcesso, Long> {
    Optional<AtribuicaoAcesso> findByIdAndMembershipIdAndOrganizacaoId(Long id, Long membershipId, Long orgId);
    boolean existsByMembershipIdAndPapelIdAndOrganizacaoIdAndRevogadoEmIsNull(Long membershipId, Long papelId, Long orgId);

    @Query(value = """
        WITH papeis_vigentes AS (
            SELECT p.id, p.codigo, p.sistema
            FROM atribuicao_acesso aa JOIN papel p ON p.id = aa.papel_id
            WHERE aa.membro_organizacao_id = :membershipId AND aa.organizacao_id = :orgId
              AND aa.escopo_tipo = 'ORGANIZACAO' AND aa.escopo_id = :orgId
              AND aa.revogado_em IS NULL AND aa.vigente_de <= :agora
              AND (aa.vigente_ate IS NULL OR aa.vigente_ate > :agora)
        )
        SELECT pe.codigo FROM permissao pe
        WHERE EXISTS (SELECT 1 FROM papeis_vigentes p WHERE p.sistema AND p.codigo = 'ADMIN_ORGANIZACAO')
           OR EXISTS (
               SELECT 1 FROM papel_permissao pp JOIN papeis_vigentes p ON p.id = pp.papel_id
               WHERE pp.permissao_id = pe.id
           )
        ORDER BY pe.codigo
        """, nativeQuery = true)
    List<String> permissoesEfetivas(
            @Param("membershipId") Long membershipId,
            @Param("orgId") Long orgId,
            @Param("agora") OffsetDateTime agora);

    @Query(value = """
        SELECT count(*) FROM atribuicao_acesso aa JOIN papel p ON p.id = aa.papel_id
        JOIN membro_organizacao m ON m.id = aa.membro_organizacao_id
        WHERE aa.organizacao_id = :orgId AND p.codigo = 'ADMIN_ORGANIZACAO'
          AND aa.revogado_em IS NULL AND aa.vigente_de <= :agora
          AND (aa.vigente_ate IS NULL OR aa.vigente_ate > :agora) AND m.status = 'ATIVO'
        """, nativeQuery = true)
    long contarAdministradoresAtivos(
            @Param("orgId") Long orgId,
            @Param("agora") OffsetDateTime agora);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM atribuicao_acesso aa JOIN papel p ON p.id = aa.papel_id
            WHERE aa.membro_organizacao_id = :membershipId AND aa.organizacao_id = :orgId
              AND p.codigo = :papelCodigo AND aa.revogado_em IS NULL
              AND aa.vigente_de <= :agora
              AND (aa.vigente_ate IS NULL OR aa.vigente_ate > :agora)
        )
        """, nativeQuery = true)
    boolean possuiPapelEfetivo(
            @Param("membershipId") Long membershipId,
            @Param("orgId") Long orgId,
            @Param("papelCodigo") String papelCodigo,
            @Param("agora") OffsetDateTime agora);
}
