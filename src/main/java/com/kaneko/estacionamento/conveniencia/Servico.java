package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Servico de conveniencia oferecido no estacionamento (ex.: lavagem, calibragem).
 * Mapeia a tabela {@code servico}.
 *
 * <p>O nome e unico ({@code uq_servico_nome}); quando informado, o valor padrao
 * nao pode ser negativo ({@code ck_servico_valor_padrao}).
 */
@Entity
@Table(name = "servico")
@Getter
@Setter
public class Servico extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    /** Valor sugerido do servico; opcional (sem default no schema). */
    @Column(name = "valor_padrao", precision = 10, scale = 2)
    private BigDecimal valorPadrao;
}
