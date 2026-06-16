package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Condicao de pagamento (raiz do agregado) com suas {@link ParcelaCondicaoPagamento
 * parcelas}. Mapeia a tabela {@code condicao_pagamento}.
 *
 * <p>O nome e unico em todo o sistema (restricao {@code uq_condicao_pagamento_nome}).
 * As parcelas detalham o vencimento ({@code dias}) e o peso ({@code percentual}) de
 * cada cobranca; quando informadas, a soma dos percentuais deve fechar em 100.
 */
@Entity
@Table(name = "condicao_pagamento")
@Getter
@Setter
public class CondicaoPagamento extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "numero_parcelas", nullable = false)
    private Integer numeroParcelas = 1;

    @Column(name = "dias_primeira_parcela", nullable = false)
    private Integer diasPrimeiraParcela = 0;

    @Column(name = "dias_entre_parcelas", nullable = false)
    private Integer diasEntreParcelas = 0;

    @Column(name = "percentual_juros", nullable = false, precision = 6, scale = 2)
    private BigDecimal percentualJuros = BigDecimal.ZERO;

    @Column(name = "percentual_multa", nullable = false, precision = 6, scale = 2)
    private BigDecimal percentualMulta = BigDecimal.ZERO;

    @Column(name = "percentual_desconto", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @OneToMany(mappedBy = "condicaoPagamento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParcelaCondicaoPagamento> parcelas = new ArrayList<>();

    /**
     * Substitui as parcelas do agregado mantendo a relacao bidirecional: limpa a
     * colecao atual (o {@code orphanRemoval} remove os orfaos) e reassocia cada
     * parcela informada a esta condicao.
     */
    public void setParcelas(List<ParcelaCondicaoPagamento> novasParcelas) {
        this.parcelas.clear();
        if (novasParcelas != null) {
            for (ParcelaCondicaoPagamento parcela : novasParcelas) {
                parcela.setCondicaoPagamento(this);
                this.parcelas.add(parcela);
            }
        }
    }
}
