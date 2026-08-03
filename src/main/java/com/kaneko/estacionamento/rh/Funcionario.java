package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.pessoas.EstadoCivil;
import com.kaneko.estacionamento.pessoas.Sexo;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Funcionario do estacionamento. Mapeia a tabela {@code funcionario}.
 *
 * <p>O CPF e unico dentro da organizacao proprietaria. O {@link Cargo} e
 * obrigatorio e deve pertencer ao mesmo tenant; cidade permanece global.
 */
@Entity
@Table(name = "funcionario")
@Getter
@Setter
public class Funcionario extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "apelido", length = 60)
    private String apelido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPessoa tipo = TipoPessoa.FISICA;

    @Column(name = "cpf", nullable = false, length = 20)
    private String cpf;

    @Column(name = "rg_inscricao_estadual", length = 20)
    private String rgInscricaoEstadual;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", length = 20)
    private Sexo sexo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", length = 20)
    private EstadoCivil estadoCivil;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "email", length = 120)
    private String email;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cargo_id", nullable = false)
    private Cargo cargo;

    @Column(name = "cnh", length = 20)
    private String cnh;

    @Column(name = "data_validade_cnh")
    private LocalDate dataValidadeCnh;

    @Column(name = "data_admissao", nullable = false)
    private LocalDate dataAdmissao;

    @Column(name = "data_demissao")
    private LocalDate dataDemissao;

    @Column(name = "salario", precision = 10, scale = 2)
    private BigDecimal salario;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
