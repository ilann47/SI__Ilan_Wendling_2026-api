package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Forma de pagamento (Dinheiro, Cartao, Pix, Boleto). Mapeia a tabela
 * {@code forma_pagamento}.
 *
 * <p>O nome e unico em todo o sistema (restricao {@code uq_forma_pagamento_nome}).
 */
@Entity
@Table(name = "forma_pagamento")
@Getter
@Setter
public class FormaPagamento extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;
}
