package com.kaneko.estacionamento.organizacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizacaoRepository extends JpaRepository<Organizacao, Long> {

    boolean existsByDocumento(String documento);
}
