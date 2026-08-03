package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
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
 * Transportadora que realiza o frete das compras (abastecimento da
 * conveniencia). Mapeia {@code transportadora}.
 *
 * <p>Documento unico ({@code uq_transportadora_documento}); cidade e condicao
 * de pagamento opcionais. Sua frota de veiculos e mantida pelo vinculo N:N
 * {@link TransportadoraVeiculoFrota}.
 */
@Entity
@Table(name = "transportadora")
@Getter
@Setter
public class Transportadora extends BaseEntity {

    @Column(name = "organizacao_id")
    private Long organizacaoId;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "nome_fantasia", length = 120)
    private String nomeFantasia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPessoa tipo = TipoPessoa.JURIDICA;

    @Column(name = "documento", nullable = false, length = 20)
    private String documento;

    @Column(name = "rg_inscricao_estadual", length = 20)
    private String rgInscricaoEstadual;

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

    @Column(name = "observacao", length = 255)
    private String observacao;
}
