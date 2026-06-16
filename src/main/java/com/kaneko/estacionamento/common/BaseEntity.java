package com.kaneko.estacionamento.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclasse dos CADASTROS. Alem da chave e dos campos de auditoria (herdados
 * de {@link AuditableEntity}), traz o indicador de exclusao logica
 * ({@code ativo}).
 *
 * <p>Entidades transacionais, que controlam seu ciclo de vida por
 * {@code status}/{@code situacao} em vez de {@code ativo}, devem estender
 * diretamente {@link AuditableEntity}.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity extends AuditableEntity {

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;
}
