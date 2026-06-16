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
 * Superclasse base das entidades de dominio.
 *
 * <p>Concentra a chave primaria ({@code id} do tipo IDENTITY), o indicador de
 * exclusao logica ({@code ativo}) e os campos de auditoria
 * ({@code created_at}/{@code updated_at}), estes geridos pelo Hibernate via
 * {@link CreationTimestamp} e {@link UpdateTimestamp} — o que tambem faz o
 * {@code updated_at} ser atualizado em cada UPDATE (o schema, sozinho, so o
 * preenche na insercao via DEFAULT now()).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
