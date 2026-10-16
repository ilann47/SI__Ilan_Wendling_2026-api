package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.membership.dto.OrganizacaoAcessivelResponse;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembroOrganizacaoRepository extends JpaRepository<MembroOrganizacao, Long> {

    boolean existsByOrganizacaoIdAndUsuarioIdAndStatusNot(
            Long organizacaoId, Long usuarioId, StatusMembroOrganizacao status);

    long countByOrganizacaoIdAndStatusNot(Long organizacaoId, StatusMembroOrganizacao status);

    Optional<MembroOrganizacao> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    Optional<MembroOrganizacao> findByIdAndUsuarioIdAndOrganizacaoIdAndStatus(
            Long id, Long usuarioId, Long organizacaoId, StatusMembroOrganizacao status);

    Optional<MembroOrganizacao> findFirstByUsuarioIdAndOrganizacaoIdAndStatusOrderByIdDesc(
            Long usuarioId, Long organizacaoId, StatusMembroOrganizacao status);

    @Query("""
            select new com.kaneko.estacionamento.membership.dto.OrganizacaoAcessivelResponse(
                o.id, o.razaoSocial, o.nomeFantasia, m.id, m.version)
            from MembroOrganizacao m, Organizacao o
            where m.organizacaoId = o.id
              and m.usuarioId = :usuarioId
              and m.status = :statusMembership
              and o.status = :statusOrganizacao
            order by o.razaoSocial, o.id
            """)
    List<OrganizacaoAcessivelResponse> listarOrganizacoesAcessiveis(
            @Param("usuarioId") Long usuarioId,
            @Param("statusMembership") StatusMembroOrganizacao statusMembership,
            @Param("statusOrganizacao") StatusOrganizacao statusOrganizacao);
}
