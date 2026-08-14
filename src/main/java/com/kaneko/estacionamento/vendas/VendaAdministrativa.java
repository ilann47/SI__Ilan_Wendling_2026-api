package com.kaneko.estacionamento.vendas;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "venda_administrativa")
@Getter
@Setter
public class VendaAdministrativa extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "local_estoque_id", nullable = false)
    private LocalEstoque localEstoque;
    @Column(nullable = false, length = 40)
    private String numero;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVendaAdministrativa status = StatusVendaAdministrativa.RASCUNHO;
    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;
    @Column(nullable = false, length = 3)
    private String moeda = "BRL";
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "valor_desconto", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;
    @Column(length = 500)
    private String observacao;
    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;
    @Version
    @Column(nullable = false)
    private Long version;
    @OneToMany(mappedBy = "vendaAdministrativa", cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("sequencia ASC")
    private List<ItemVendaAdministrativa> itens = new ArrayList<>();

    public void adicionarItem(ItemVendaAdministrativa item) {
        item.setVendaAdministrativa(this);
        item.setOrganizacaoId(organizacaoId);
        itens.add(item);
    }

    public void confirmar() {
        validarConfirmacao();
        status = StatusVendaAdministrativa.CONFIRMADA;
    }

    public void validarConfirmacao() {
        if (status != StatusVendaAdministrativa.RASCUNHO) {
            throw new ConflitoException("Somente uma venda em rascunho pode ser confirmada.");
        }
        if (itens.isEmpty()) {
            throw new ConflitoException("A venda deve possuir ao menos um item.");
        }
    }

    public void cancelar(String motivo) {
        if (status != StatusVendaAdministrativa.RASCUNHO) {
            throw new ConflitoException("Somente uma venda em rascunho pode ser cancelada.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("O motivo do cancelamento e obrigatorio.");
        }
        motivoCancelamento = motivo;
        status = StatusVendaAdministrativa.CANCELADA;
    }
}
