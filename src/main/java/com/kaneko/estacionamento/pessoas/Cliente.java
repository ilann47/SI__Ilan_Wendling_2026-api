package com.kaneko.estacionamento.pessoas;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Cliente do estacionamento (pessoa fisica ou juridica). Mapeia a tabela
 * {@code cliente}.
 *
 * <p>O documento (CPF/CNPJ) e unico dentro da organizacao proprietaria.
 * As referencias a cidade, nacionalidade (pais) e condicao de pagamento sao
 * opcionais.
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
public class Cliente extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "apelido", length = 60)
    private String apelido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPessoa tipo = TipoPessoa.FISICA;

    @Column(name = "documento", nullable = false, length = 20)
    private String documento;

    @Column(name = "rg_inscricao_estadual", length = 20)
    private String rgInscricaoEstadual;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", length = 20)
    private Sexo sexo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", length = 20)
    private EstadoCivil estadoCivil;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "endereco", length = 120)
    private String endereco;

    @Column(name = "numero", length = 10)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "bairro", length = 60)
    private String bairro;

    @Column(name = "cep", length = 9)
    private String cep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cidade_id")
    private Cidade cidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @Column(name = "limite_credito", nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
