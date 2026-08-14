package com.kaneko.estacionamento.ordensservico;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.common.exception.ConflitoException;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ordem_servico")
@Getter
@Setter
public class OrdemServico extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @Column(nullable = false, length = 40)
    private String numero;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusOrdemServico status = StatusOrdemServico.RASCUNHO;
    @Column(name = "data_abertura", nullable = false)
    private LocalDate dataAbertura;
    @Column(name = "previsao_conclusao")
    private LocalDate previsaoConclusao;
    @Column(name = "iniciada_em")
    private OffsetDateTime iniciadaEm;
    @Column(name = "concluida_em")
    private OffsetDateTime concluidaEm;
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
    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequencia ASC")
    private List<ItemOrdemServico> itens = new ArrayList<>();

    public void adicionarItem(ItemOrdemServico item) {
        item.setOrdemServico(this);
        item.setOrganizacaoId(organizacaoId);
        itens.add(item);
    }

    public void iniciar() {
        if (status != StatusOrdemServico.RASCUNHO) {
            throw new ConflitoException("Somente uma ordem em rascunho pode ser iniciada.");
        }
        if (itens.isEmpty()) {
            throw new ConflitoException("A ordem deve possuir ao menos um item.");
        }
        status = StatusOrdemServico.EM_EXECUCAO;
        iniciadaEm = OffsetDateTime.now();
    }

    public void concluir() {
        if (status != StatusOrdemServico.EM_EXECUCAO) {
            throw new ConflitoException("Somente uma ordem em execucao pode ser concluida.");
        }
        status = StatusOrdemServico.CONCLUIDA;
        concluidaEm = OffsetDateTime.now();
    }

    public void cancelar(String motivo) {
        if (status != StatusOrdemServico.RASCUNHO && status != StatusOrdemServico.EM_EXECUCAO) {
            throw new ConflitoException("Uma ordem concluida ou cancelada nao pode ser cancelada.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("O motivo do cancelamento e obrigatorio.");
        }
        motivoCancelamento = motivo.trim();
        status = StatusOrdemServico.CANCELADA;
    }
}
