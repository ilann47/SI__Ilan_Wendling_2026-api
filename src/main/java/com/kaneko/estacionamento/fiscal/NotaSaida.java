package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.pessoas.Cliente;
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

    @OneToMany(mappedBy = "notaSaida", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemNotaSaida> itens = new ArrayList<>();

    /** Adiciona um item mantendo a relacao bidirecional. */
    public void addItem(ItemNotaSaida item) {
        item.setNotaSaida(this);
        this.itens.add(item);
    }
}
