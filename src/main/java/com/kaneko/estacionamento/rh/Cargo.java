package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Cargo (funcao) do funcionario. Mapeia a tabela {@code cargo}.
 *
 * <p>O nome e unico dentro da organizacao proprietaria. Registros historicos
 * podem permanecer sem owner ate a reconciliacao explicita.
 */
@Entity
@Table(name = "cargo")
@Getter
@Setter
public class Cargo extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "salario_base_micros", nullable = false, precision = 14, scale = 0)
    private BigDecimal salarioBase = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "carga_horaria_micros", precision = 10, scale = 0)
    private BigDecimal cargaHoraria;

    @Column(name = "requer_cnh", nullable = false)
    private Boolean requerCnh = false;
}
