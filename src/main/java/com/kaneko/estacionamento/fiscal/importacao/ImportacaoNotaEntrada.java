package com.kaneko.estacionamento.fiscal.importacao;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "importacao_nota_entrada") @Getter @Setter
public class ImportacaoNotaEntrada extends AuditableEntity {
    @Column(name="organizacao_id", nullable=false, updatable=false) private Long organizacaoId;
    @Column(name="ator_id", nullable=false, updatable=false) private Long atorId;
    @Column(nullable=false, length=44, updatable=false) private String chave;
    @Column(name="xml_original", nullable=false, updatable=false, columnDefinition="bytea") private byte[] xmlOriginal;
    @Column(name="xml_hash", nullable=false, length=64, updatable=false) private String xmlHash;
    @Column(nullable=false, length=20, updatable=false) private String numero;
    @Column(name="emitente_nome", nullable=false, length=120, updatable=false) private String emitenteNome;
    @Column(nullable=false, precision=15, scale=2, updatable=false) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private StatusImportacaoNota status;
    @Version @Column(nullable=false) private Long version;
    @Column(name="fornecedor_id") private Long fornecedorId;
    @Column(name="local_estoque_id") private Long localEstoqueId;
    @Column(name="condicao_pagamento_id") private Long condicaoPagamentoId;
    @Column(name="ordem_compra_id") private Long ordemCompraId;
    @Column(name="recebimento_compra_id") private Long recebimentoCompraId;
    @Column(name="registrar_novo_recebimento", nullable=false) private boolean registrarNovoRecebimento;
    @Column(name="nota_entrada_id") private Long notaEntradaId;
    @Column(name="conferido_por") private Long conferidoPor;
    @Column(name="conferido_em") private OffsetDateTime conferidoEm;
    @Column(name="revisado_em", nullable=false) private OffsetDateTime revisadoEm;
    @OneToMany(mappedBy="importacao", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("sequencia ASC") private List<ItemImportacaoNotaEntrada> itens = new ArrayList<>();

    public void adicionar(ItemImportacaoNotaEntrada item) {
        item.setImportacao(this);
        item.setOrganizacaoId(organizacaoId);
        itens.add(item);
    }
}
