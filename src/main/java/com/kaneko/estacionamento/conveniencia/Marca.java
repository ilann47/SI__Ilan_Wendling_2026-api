package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Marca — fabricante/marca dos produtos do modulo de conveniencia. Mapeia a
 * tabela {@code marca}.
 *
 * <p>O nome e unico dentro da organizacao proprietaria. Registros historicos
 * podem permanecer sem owner ate a reconciliacao explicita.
 */
@Entity
@Table(name = "marca")
@Getter
@Setter
public class Marca extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;
}
