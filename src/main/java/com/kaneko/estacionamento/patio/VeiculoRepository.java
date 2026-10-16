package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Veiculo}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega o {@code cliente}
 * associado (quando houver) via {@link EntityGraph}.
 */
public interface VeiculoRepository extends JpaRepository<Veiculo, Long>,
        JpaSpecificationExecutor<Veiculo> {

    /** Unicidade da placa (uq_veiculo_placa). */
    boolean existsByOrganizacaoIdAndPlaca(Long organizacaoId, String placa);
    boolean existsByOrganizacaoIdIsNullAndPlaca(String placa);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByOrganizacaoIdAndPlacaAndIdNot(Long organizacaoId, String placa, Long id);
    boolean existsByOrganizacaoIdIsNullAndPlacaAndIdNot(String placa, Long id);

    @EntityGraph(attributePaths = "cliente")
    Optional<Veiculo> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = "cliente")
    Optional<Veiculo> findByIdAndOrganizacaoIdIsNull(Long id);
}
