package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.FormaPagamento;
import com.kaneko.estacionamento.patio.Mensalista;
import com.kaneko.estacionamento.patio.Movimentacao;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.ordensservico.OrdemServico;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaServicoResponse;
import com.kaneko.estacionamento.fiscal.dto.ParcelaNotaEntradaResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Nota fiscal de servico (NFS-e) do estacionamento/lavagem, com destaque de ISS.
 * Mapeia {@code nota_servico}.
 *
 * <p>A origem do servico pode ser uma {@link Movimentacao} (avulso) ou um
 * {@link Mensalista} (mensalidade), nunca ambos ao mesmo tempo
 * ({@code ck_nota_servico_origem}).
 */
@Entity
@Table(name = "nota_servico")
@Getter
@Setter
public class NotaServico extends AuditableEntity {
    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "modelo", nullable = false, length = 10)
    private String modelo = "55";

    @Column(name = "serie", nullable = false, length = 10)
    private String serie = "1";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimentacao_id")
    private Movimentacao movimentacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mensalista_id")
    private Mensalista mensalista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordem_servico_id")
    private OrdemServico ordemServico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_servico_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorServico = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "aliquota_iss_micros", nullable = false, precision = 9, scale = 0)
    private BigDecimal aliquotaIss = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_iss_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorIss = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_desconto_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_total_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoNotaServico situacao = SituacaoNotaServico.PENDENTE;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "itens_conferidos", nullable = false, columnDefinition = "jsonb")
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<ItemPersistido> itens = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parcelas_conferidas", nullable = false, columnDefinition = "jsonb")
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<ParcelaPersistida> parcelas = List.of();

    @Column(name = "cronograma_definido", nullable = false)
    private boolean cronogramaDefinido;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    @Column(name = "cancelada_em")
    private OffsetDateTime canceladaEm;

    public List<ItemNotaServicoResponse> getItens() {
        return itens.stream().map(ItemPersistido::resposta).toList();
    }

    public void setItens(List<ItemNotaServicoResponse> itens) {
        this.itens = itens.stream().map(ItemPersistido::de).toList();
    }

    public List<ParcelaNotaEntradaResponse> getParcelas() {
        return parcelas.stream().map(ParcelaPersistida::resposta).toList();
    }

    public void setParcelas(List<ParcelaNotaEntradaResponse> parcelas) {
        this.parcelas = parcelas.stream().map(ParcelaPersistida::de).toList();
    }

    private static BigInteger micros(BigDecimal valor) {
        return valor == null ? null : valor.movePointRight(MilionesimosConverter.ESCALA).toBigIntegerExact();
    }

    private static BigDecimal decimal(BigInteger valor) {
        return valor == null ? null : new BigDecimal(valor, MilionesimosConverter.ESCALA);
    }

    /** Os snapshots persistem somente inteiros; a API continua nas unidades originais. */
    public record ItemPersistido(int sequencia, Long servicoId, String descricao,
            BigInteger quantidade, BigInteger valorUnitario, BigInteger valorDesconto, BigInteger valorTotal) {
        static ItemPersistido de(ItemNotaServicoResponse item) {
            return new ItemPersistido(item.sequencia(), item.servicoId(), item.descricao(),
                    micros(item.quantidade()), micros(item.valorUnitario()), micros(item.valorDesconto()), micros(item.valorTotal()));
        }

        ItemNotaServicoResponse resposta() {
            return new ItemNotaServicoResponse(sequencia, servicoId, descricao,
                    decimal(quantidade), decimal(valorUnitario), decimal(valorDesconto), decimal(valorTotal));
        }
    }

    public record ParcelaPersistida(int numeroParcela, int totalParcelas, BigInteger valor,
            LocalDate dataVencimento, Long formaPagamentoId, String formaPagamentoNome) {
        static ParcelaPersistida de(ParcelaNotaEntradaResponse parcela) {
            return new ParcelaPersistida(parcela.numeroParcela(), parcela.totalParcelas(), micros(parcela.valor()),
                    parcela.dataVencimento(), parcela.formaPagamentoId(), parcela.formaPagamentoNome());
        }

        ParcelaNotaEntradaResponse resposta() {
            return new ParcelaNotaEntradaResponse(numeroParcela, totalParcelas, decimal(valor),
                    dataVencimento, formaPagamentoId, formaPagamentoNome);
        }
    }
}
