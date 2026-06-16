package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Cargo (funcao) do funcionario. Mapeia a tabela {@code cargo}.
 *
 * <p>O nome e unico ({@code uq_cargo_nome}); valores nao-negativos garantidos
 * por {@code ck_cargo_valores}.
 */
@Entity
@Table(name = "cargo")
@Getter
@Setter
public class Cargo extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "salario_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal salarioBase = BigDecimal.ZERO;

    @Column(name = "carga_horaria", precision = 6, scale = 2)
    private BigDecimal cargaHoraria;

    @Column(name = "requer_cnh", nullable = false)
    private Boolean requerCnh = false;
}
