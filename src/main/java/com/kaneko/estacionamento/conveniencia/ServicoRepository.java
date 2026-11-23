package com.kaneko.estacionamento.conveniencia;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositorio de {@link Servico}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}; os filtros dinamicos das listagens usam
 * {@link JpaSpecificationExecutor}.
 */
public interface ServicoRepository extends JpaRepository<Servico, Long>,
        JpaSpecificationExecutor<Servico> {

    /** Indica se ja existe um servico com o nome informado (unicidade uq_servico_nome). */
    Optional<Servico> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    Optional<Servico> findByIdAndOrganizacaoIdIsNull(Long id);

    @Query("""
            select s from Servico s
            where s.organizacaoId=:org and s.ativo=true
              and (:busca='' or locate(lower(:busca), lower(s.nome)) > 0)
            order by lower(s.nome), s.id
            """)
    Page<Servico> procurarAtivos(Long org, String busca, Pageable pageable);

    /** Indica se existe OUTRO servico (id diferente) com o mesmo nome — usado em alteracoes. */
    @Query("""
            select count(s)>0 from Servico s where s.organizacaoId=:org
            and lower(trim(s.nome))=lower(trim(:nome)) and (:id is null or s.id<>:id)
            """)
    boolean existeNomeNormalizado(Long org, String nome, Long id);
}
