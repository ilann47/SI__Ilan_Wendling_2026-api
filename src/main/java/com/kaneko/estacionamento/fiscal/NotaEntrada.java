package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OrderBy;
import com.kaneko.estacionamento.logistica.Transportadora;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
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

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_estoque_id")
    private LocalEstoque localEstoque;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportadora_id")
    private Transportadora transportadora;

    @Column(name = "observacao_interna", length = 1000)
    private String observacaoInterna;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_chegada")
    private LocalDate dataChegada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_frete", nullable = false, length = 3)
    private TipoFrete tipoFrete = TipoFrete.CIF;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_produtos_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorProdutos = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_frete_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_seguro_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorSeguro = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "outras_despesas_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal outrasDespesas = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_desconto_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_total_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoNota situacao = SituacaoNota.PENDENTE;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @Column(name = "revertida_em")
    private java.time.OffsetDateTime revertidaEm;

    @Column(name = "motivo_reversao", length = 500)
    private String motivoReversao;

    @OneToMany(mappedBy = "notaEntrada", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequencia ASC")
    private List<ItemNotaEntrada> itens = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "parcela_nota_entrada", joinColumns = @JoinColumn(name = "nota_entrada_id"))
    @OrderBy("numeroParcela ASC")
    private List<ParcelaNotaEntrada> parcelas = new ArrayList<>();

    @Column(name = "cronograma_definido", nullable = false)
    private boolean cronogramaDefinido;

    /** Adiciona um item mantendo a relacao bidirecional. */
    public void addItem(ItemNotaEntrada item) {
        item.setNotaEntrada(this);
        this.itens.add(item);
    }
}
