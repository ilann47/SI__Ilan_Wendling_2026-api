package com.kaneko.estacionamento.fornecedor;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.logistica.Transportadora;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Fornecedor (raiz do agregado), com seus e-mails e telefones de contato.
 * Mapeia {@code fornecedor}.
 *
 * <p>Documento unico ({@code uq_fornecedor_documento}); cidade, nacionalidade
 * (pais) e condicao de pagamento opcionais. As colecoes de contato sao parte
 * do agregado (cascade total, orphanRemoval).
 */
@Entity
@Table(name = "fornecedor")
@Getter
@Setter
public class Fornecedor extends BaseEntity {

    @Column(name = "organizacao_id")
    private Long organizacaoId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "nome_fantasia", length = 120)
    private String nomeFantasia;

    @Column(name = "apelido", length = 60)
    private String apelido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPessoa tipo = TipoPessoa.JURIDICA;

    @Column(name = "documento", nullable = false, length = 20)
    private String documento;

    @Column(name = "rg_inscricao_estadual", length = 20)
    private String rgInscricaoEstadual;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportadora_id")
    private Transportadora transportadora;

    @Column(name = "limite_credito", nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @OneToMany(mappedBy = "fornecedor", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FornecedorEmail> emails = new LinkedHashSet<>();

    @OneToMany(mappedBy = "fornecedor", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FornecedorTelefone> telefones = new LinkedHashSet<>();
}
