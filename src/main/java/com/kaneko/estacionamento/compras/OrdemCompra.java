package com.kaneko.estacionamento.compras;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.logistica.Transportadora;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.usuario.Usuario;
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
@Table(name = "ordem_compra")
@Getter
@Setter
public class OrdemCompra extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(name = "numero_nota", nullable = false, length = 60)
    private String numeroNota;

    @Column(name = "serie_nota", nullable = false, length = 20)
    private String serieNota;

    @Column(name = "modelo_nota", nullable = false, length = 10)
    private String modeloNota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusOrdemCompra status = StatusOrdemCompra.RASCUNHO;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "previsao_entrega")
    private LocalDate previsaoEntrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportadora_id")
    private Transportadora transportadora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_estoque_entrega_id")
    private LocalEstoque localEstoqueEntrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprador_id")
    private Usuario comprador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_frete", nullable = false, length = 20)
    private TipoFreteOrdemCompra tipoFrete = TipoFreteOrdemCompra.SEM_FRETE;

    @Column(name = "referencia_fornecedor", length = 60)
    private String referenciaFornecedor;

    @Column(nullable = false, length = 3)
    private String moeda = "BRL";

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

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

    @Column(length = 500)
    private String observacao;

    @Column(name = "observacao_interna", length = 1000)
    private String observacaoInterna;

    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "ordemCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequencia ASC")
    private List<ItemOrdemCompra> itens = new ArrayList<>();

    public void adicionarItem(ItemOrdemCompra item) {
        item.setOrdemCompra(this);
        item.setOrganizacaoId(organizacaoId);
        itens.add(item);
    }

    public void aprovar() {
        exigirStatus(StatusOrdemCompra.RASCUNHO, "Somente uma ordem em rascunho pode ser aprovada.");
        if (itens.isEmpty()) {
            throw new ConflitoException("A ordem deve possuir ao menos um item para ser aprovada.");
        }
        status = StatusOrdemCompra.APROVADA;
    }

    public void cancelar(String motivo) {
        boolean possuiRecebimento = itens.stream()
                .anyMatch(item -> item.getQuantidadeRecebida().signum() > 0);
        if (possuiRecebimento
                || (status != StatusOrdemCompra.RASCUNHO && status != StatusOrdemCompra.APROVADA)) {
            throw new ConflitoException("Uma ordem com recebimento nao pode ser cancelada.");
        }
        status = StatusOrdemCompra.CANCELADA;
        motivoCancelamento = motivo;
    }

    public void rejeitarEntrega(String motivo) {
        exigirStatus(StatusOrdemCompra.APROVADA,
                "Somente uma entrega aguardando recebimento pode ser rejeitada.");
        status = StatusOrdemCompra.REJEITADA;
        motivoCancelamento = motivo;
    }

    public void atualizarStatusRecebimento() {
        boolean completa = itens.stream()
                .allMatch(item -> item.getQuantidadeRecebida()
                        .compareTo(item.getQuantidadePedida()) == 0);
        if (!completa) {
            throw new ConflitoException("O recebimento deve contemplar integralmente todos os itens da ordem.");
        }
        status = StatusOrdemCompra.RECEBIDA;
    }

    public void exigirRecebivel() {
        if (status != StatusOrdemCompra.APROVADA) {
            throw new ConflitoException("A ordem nao esta disponivel para recebimento.");
        }
    }

    private void exigirStatus(StatusOrdemCompra esperado, String mensagem) {
        if (status != esperado) {
            throw new ConflitoException(mensagem);
        }
    }
}
