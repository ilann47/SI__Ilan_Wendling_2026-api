package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Nota fiscal de entrada (compra de mercadoria), raiz do agregado com seus
 * {@link ItemNotaEntrada itens}. Mapeia {@code nota_entrada}.
 *
 * <p>Nasce {@code PENDENTE}. Ao ser confirmada, soma o estoque dos produtos e
 * gera os titulos a pagar — efeitos coordenados pelo {@code NotaEntradaService}.
 */
@Entity
@Table(name = "nota_entrada")
@Getter
@Setter
public class NotaEntrada extends AuditableEntity {

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "modelo", nullable = false, length = 10)
    private String modelo = "55";

    @Column(name = "serie", nullable = false, length = 10)
    private String serie = "1";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_chegada")
    private LocalDate dataChegada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_frete", nullable = false, length = 3)
    private TipoFrete tipoFrete = TipoFrete.CIF;

    @Column(name = "valor_produtos", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorProdutos = BigDecimal.ZERO;

    @Column(name = "valor_frete", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @Column(name = "valor_seguro", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorSeguro = BigDecimal.ZERO;

    @Column(name = "outras_despesas", nullable = false, precision = 15, scale = 2)
    private BigDecimal outrasDespesas = BigDecimal.ZERO;

    @Column(name = "valor_desconto", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoNota situacao = SituacaoNota.PENDENTE;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @OneToMany(mappedBy = "notaEntrada", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemNotaEntrada> itens = new ArrayList<>();

    /** Adiciona um item mantendo a relacao bidirecional. */
    public void addItem(ItemNotaEntrada item) {
        item.setNotaEntrada(this);
        this.itens.add(item);
    }
}
