package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Marca — fabricante/marca dos produtos do modulo de conveniencia. Mapeia a
 * tabela {@code marca}.
 *
 * <p>O nome e unico em todo o sistema (restricao {@code uq_marca_nome}).
 */
@Entity
@Table(name = "marca")
@Getter
@Setter
public class Marca extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;
}
