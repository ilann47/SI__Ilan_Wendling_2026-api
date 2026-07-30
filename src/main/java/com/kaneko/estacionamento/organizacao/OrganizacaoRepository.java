package com.kaneko.estacionamento.organizacao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizacaoRepository extends JpaRepository<Organizacao, Long> {

    boolean existsByDocumento(String documento);

    Optional<Organizacao> findByIdAndStatus(Long id, StatusOrganizacao status);
}
