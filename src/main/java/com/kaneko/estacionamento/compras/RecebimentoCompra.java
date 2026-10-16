package com.kaneko.estacionamento.compras;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.usuario.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recebimento_compra")
@Getter
@Setter
public class RecebimentoCompra extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_compra_id", nullable = false, updatable = false)
    private OrdemCompra ordemCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "local_estoque_id", nullable = false, updatable = false)
    private LocalEstoque localEstoque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ator_id", nullable = false, updatable = false)
    private Usuario ator;

    @Column(name = "recebido_em", nullable = false, updatable = false)
    private OffsetDateTime recebidoEm;

    @Column(length = 500, updatable = false)
    private String observacao;

    @OneToMany(mappedBy = "recebimentoCompra", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<ItemRecebimentoCompra> itens = new ArrayList<>();

    public void adicionarItem(ItemRecebimentoCompra item) {
        item.setRecebimentoCompra(this);
        item.setOrdemCompraId(ordemCompra.getId());
        item.setOrganizacaoId(organizacaoId);
        itens.add(item);
    }
}
