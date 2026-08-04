package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.pessoas.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de mensalista — liga um {@link Cliente} a um {@link Veiculo}. Mapeia
 * a tabela {@code mensalista}.
 *
 * <p>O ciclo de vida e controlado por {@code status} (ATIVO/SUSPENSO/CANCELADO);
 * o banco garante no maximo um contrato ATIVO por veiculo
 * ({@code uq_mensalista_veiculo_ativo}).
 */
@Entity
@Table(name = "mensalista")
@Getter
@Setter
public class Mensalista extends AuditableEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(name = "valor_mensal", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensal;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusMensalista status = StatusMensalista.ATIVO;
}
