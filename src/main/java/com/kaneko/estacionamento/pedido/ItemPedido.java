package com.kaneko.estacionamento.pedido;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "item_pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPedido extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, updatable = false)
    private Pedido pedido;
    @Column(name = "produto_id", nullable = false, updatable = false)
    private Long produtoId;
    @Column(name = "alocacao_id", nullable = false, updatable = false)
    private Long alocacaoId;
    @Column(name = "lote_preco_id", nullable = false, updatable = false)
    private Long lotePrecoId;
    @Column(name = "nome_produto", nullable = false, updatable = false, length = 160)
    private String nomeProduto;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private CategoriaProduto categoria;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private DireitoProduto direito;
    @Column(name = "inicio_acesso", nullable = false, updatable = false)
    private OffsetDateTime inicioAcesso;
    @Column(name = "fim_acesso", nullable = false, updatable = false)
    private OffsetDateTime fimAcesso;
    @Column(nullable = false, updatable = false)
    private Integer quantidade;
    @Column(name = "preco_unitario", nullable = false, updatable = false,
            precision = 19, scale = 2)
    private BigDecimal precoUnitario;
    @Column(nullable = false, updatable = false, length = 3)
    private String moeda;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal total;

    public static ItemPedido criar(
            Pedido pedido, Long produtoId, String nomeProduto, Integer quantidade,
            BigDecimal precoUnitario, String moeda) {
        return criar(pedido, produtoId, 0L, 0L, nomeProduto,
                CategoriaProduto.COMUM, DireitoProduto.ESTACIONAMENTO_EVENTO,
                OffsetDateTime.parse("2099-01-01T00:00:00Z"),
                OffsetDateTime.parse("2099-01-02T00:00:00Z"),
                quantidade, precoUnitario, moeda);
    }

    public static ItemPedido criar(
            Pedido pedido, Long produtoId, Long alocacaoId, Long lotePrecoId,
            String nomeProduto, CategoriaProduto categoria, DireitoProduto direito,
            OffsetDateTime inicioAcesso, OffsetDateTime fimAcesso, Integer quantidade,
            BigDecimal precoUnitario, String moeda) {
        if (pedido == null || produtoId == null || alocacaoId == null || lotePrecoId == null
                || categoria == null || direito == null) {
            throw new IllegalArgumentException("O snapshot do item esta incompleto.");
        }
        if (nomeProduto == null || nomeProduto.isBlank() || nomeProduto.trim().length() > 160) {
            throw new IllegalArgumentException("O nome do produto e invalido.");
        }
        if (inicioAcesso == null || fimAcesso == null
                || !fimAcesso.toInstant().isAfter(inicioAcesso.toInstant())) {
            throw new IllegalArgumentException("A janela de acesso do item e invalida.");
        }
        if (quantidade == null || quantidade < 1) {
            throw new IllegalArgumentException("A quantidade do item e invalida.");
        }
        BigDecimal preco = preco(precoUnitario);
        ItemPedido item = new ItemPedido();
        item.pedido = pedido;
        item.produtoId = produtoId;
        item.alocacaoId = alocacaoId;
        item.lotePrecoId = lotePrecoId;
        item.nomeProduto = nomeProduto.trim();
        item.categoria = categoria;
        item.direito = direito;
        item.inicioAcesso = inicioAcesso;
        item.fimAcesso = fimAcesso;
        item.quantidade = quantidade;
        item.precoUnitario = preco;
        item.moeda = moeda(moeda);
        item.total = preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2);
        return item;
    }

    private static BigDecimal preco(BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O preco do item e invalido.");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String moeda(String valor) {
        if (valor == null) throw new IllegalArgumentException("A moeda e obrigatoria.");
        try {
            return Currency.getInstance(valor.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A moeda do item e invalida.", ex);
        }
    }
}
