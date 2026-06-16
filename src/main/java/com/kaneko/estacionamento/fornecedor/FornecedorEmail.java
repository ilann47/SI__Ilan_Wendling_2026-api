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
 * E-mail de contato de um {@link Fornecedor}. Mapeia {@code fornecedor_email}.
 * No maximo um e-mail principal por fornecedor ({@code uq_fornecedor_email_principal}).
 */
@Entity
@Table(name = "fornecedor_email")
@Getter
@Setter
public class FornecedorEmail extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private TipoEmail tipo;

    @Column(name = "principal", nullable = false)
    private Boolean principal = false;
}
