package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.pessoas.Cliente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Nota fiscal de saida (venda de mercadoria), raiz do agregado com seus
 * {@link ItemNotaSaida itens}. Mapeia {@code nota_saida}.
 *
 * <p>Ao ser confirmada, valida e baixa o estoque dos produtos e gera os titulos
 * a receber — efeitos coordenados pelo {@code NotaSaidaService}.
 */
@Entity
@Table(name = "nota_saida")
@Getter
@Setter
public class NotaSaida extends AuditableEntity {
    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "modelo", nullable = false, length = 10)
    private String modelo = "55";

    @Column(name = "serie", nullable = false, length = 10)
    private String serie = "1";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_estoque_id")
    private LocalEstoque localEstoque;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_saida")
    private LocalDate dataSaida;

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

    @OneToMany(mappedBy = "notaSaida", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequencia ASC")
    private List<ItemNotaSaida> itens = new ArrayList<>();

    @Column(name = "cronograma_definido", nullable = false)
    private boolean cronogramaDefinido;

    @ElementCollection
    @CollectionTable(name = "parcela_nota_saida", joinColumns = @JoinColumn(name = "nota_saida_id"))
    @OrderBy("numeroParcela ASC")
    private List<ParcelaNotaSaida> parcelas = new ArrayList<>();

    /** Adiciona um item mantendo a relacao bidirecional. */
    public void addItem(ItemNotaSaida item) {
        item.setNotaSaida(this);
        this.itens.add(item);
    }
}
