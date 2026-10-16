package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.geografia.Cidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Patio — o estabelecimento (filial) de estacionamento gerido pelo app. Mapeia a
 * tabela {@code patio}.
 *
 * <p>Tem uma capacidade total de vagas ({@code capacidadeVagas}); a ocupacao
 * atual e derivada das {@link Movimentacao movimentacoes} ABERTAS vinculadas a
 * ele (nao e armazenada). Cidade opcional para composicao do endereco.
 */
@Entity
@Table(name = "patio")
@Getter
@Setter
public class Patio extends BaseEntity {

    @Column(name = "organizacao_id", insertable = false, updatable = false)
    private Long organizacaoId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "endereco", length = 120)
    private String endereco;

    @Column(name = "numero", length = 10)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "bairro", length = 60)
    private String bairro;

    @Column(name = "cep", length = 9)
    private String cep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cidade_id")
    private Cidade cidade;

    @Column(name = "capacidade_vagas", nullable = false)
    private Integer capacidadeVagas;

    @Column(name = "capacidade_operacional", nullable = false)
    private Integer capacidadeOperacional;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
