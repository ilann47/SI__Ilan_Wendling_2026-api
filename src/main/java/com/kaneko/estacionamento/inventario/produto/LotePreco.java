package com.kaneko.estacionamento.inventario.produto;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lote_preco")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotePreco extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "produto_id", nullable = false, updatable = false)
    private Long produtoId;
    @Column(nullable = false, length = 160)
    private String nome;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal preco;
    @Column(nullable = false, length = 3)
    private String moeda;
    @Column(name = "inicio_venda", nullable = false)
    private OffsetDateTime inicioVenda;
    @Column(name = "fim_venda", nullable = false)
    private OffsetDateTime fimVenda;
    @Column(nullable = false)
    private Integer quantidade;
    @Column(nullable = false)
    private Integer prioridade;
    @Version
    @Column(nullable = false)
    private Long version;

    public static LotePreco criar(
            Long organizacaoId, Long produtoId, String nome, BigDecimal preco,
            String moeda, OffsetDateTime inicioVenda, OffsetDateTime fimVenda,
            Integer quantidade, Integer prioridade) {
        if (organizacaoId == null || produtoId == null) {
            throw new IllegalArgumentException("Organizacao e produto sao obrigatorios.");
        }
        validarDados(nome, preco, moeda, inicioVenda, fimVenda, quantidade, prioridade);
        LotePreco lote = new LotePreco();
        lote.organizacaoId = organizacaoId;
        lote.produtoId = produtoId;
        lote.nome = textoObrigatorio(nome, "nome", 160);
        lote.preco = normalizarPreco(preco);
        lote.moeda = normalizarMoeda(moeda);
        lote.inicioVenda = inicioVenda;
        lote.fimVenda = fimVenda;
        lote.quantidade = quantidade;
        lote.prioridade = prioridade;
        return lote;
    }

    static void validarDados(
            String nome, BigDecimal preco, String moeda,
            OffsetDateTime inicioVenda, OffsetDateTime fimVenda,
            Integer quantidade, Integer prioridade) {
        textoObrigatorio(nome, "nome", 160);
        normalizarPreco(preco);
        normalizarMoeda(moeda);
        if (inicioVenda == null || fimVenda == null
                || !fimVenda.toInstant().isAfter(inicioVenda.toInstant())) {
            throw new IllegalArgumentException(
                    "O fim da janela comercial deve ser posterior ao inicio.");
        }
        if (quantidade == null || quantidade < 1) {
            throw new IllegalArgumentException("A quantidade deve ser de pelo menos uma vaga.");
        }
        if (prioridade == null || prioridade < 0) {
            throw new IllegalArgumentException("A prioridade nao pode ser negativa.");
        }
    }

    private static BigDecimal normalizarPreco(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("O preco e obrigatorio e nao pode ser negativo.");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O preco deve possuir no maximo duas casas decimais.");
        }
        BigDecimal normalizado = valor.setScale(2, RoundingMode.UNNECESSARY);
        if (normalizado.precision() - normalizado.scale() > 17) {
            throw new IllegalArgumentException("O preco excede o limite suportado.");
        }
        return normalizado;
    }

    private static String normalizarMoeda(String valor) {
        String codigo = textoObrigatorio(valor, "moeda", 3).toUpperCase(Locale.ROOT);
        if (codigo.length() != 3) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 de tres letras.");
        }
        try {
            return Currency.getInstance(codigo).getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 valido.", ex);
        }
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException(
                    "O campo " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }
}
