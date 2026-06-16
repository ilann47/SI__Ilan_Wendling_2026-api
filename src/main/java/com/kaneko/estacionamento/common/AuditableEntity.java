package com.kaneko.estacionamento.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Superclasse com a chave primaria ({@code id} IDENTITY) e os campos de
 * auditoria ({@code created_at}/{@code updated_at}), comum a todas as
 * entidades — inclusive as transacionais (mensalista, movimentacao, notas,
 * contas) que NAO possuem o indicador de exclusao logica {@code ativo}.
 *
 * <p>Os timestamps sao geridos pelo Hibernate ({@link CreationTimestamp} e
 * {@link UpdateTimestamp}).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
