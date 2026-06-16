package com.kaneko.estacionamento.fornecedor;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Telefone de contato de um {@link Fornecedor}. Mapeia
 * {@code fornecedor_telefone}. No maximo um principal por fornecedor
 * ({@code uq_fornecedor_telefone_principal}).
 */
@Entity
@Table(name = "fornecedor_telefone")
@Getter
@Setter
public class FornecedorTelefone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private TipoTelefone tipo;

    @Column(name = "principal", nullable = false)
    private Boolean principal = false;
}
