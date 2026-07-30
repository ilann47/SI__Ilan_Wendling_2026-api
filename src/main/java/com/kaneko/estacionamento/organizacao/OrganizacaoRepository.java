package com.kaneko.estacionamento.organizacao;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizacaoRepository extends JpaRepository<Organizacao, Long> {

    boolean existsByDocumento(String documento);

    Optional<Organizacao> findByIdAndStatus(Long id, StatusOrganizacao status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organizacao o where o.id = :id")
    Optional<Organizacao> findByIdForUpdate(@Param("id") Long id);
}
