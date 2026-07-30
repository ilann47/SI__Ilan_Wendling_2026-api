package com.kaneko.estacionamento.pedido;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pedido extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "reserva_inventario_id", nullable = false, updatable = false)
    private Long reservaInventarioId;
    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;
    @Column(nullable = false, updatable = false, length = 40)
    private String numero;
    @Column(nullable = false, updatable = false, length = 30)
    private String canal;
    @Column(name = "placa_veiculo", updatable = false, length = 7)
    private String placaVeiculo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal desconto;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxa;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    @Column(nullable = false, length = 3)
    private String moeda;
    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;
    @Column(name = "finalizado_em")
    private OffsetDateTime finalizadoEm;
    @Version
    @Column(nullable = false)
    private Long version;
    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true,
            optional = false)
    private CompradorPedido comprador;
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<ItemPedido> itens = new ArrayList<>();

    public static Pedido criar(
            Long organizacaoId, Long eventoId, Long reservaInventarioId,
            Long atorId, String numero, String canal, String nomeComprador,
            String emailComprador, String documentoComprador, String placaVeiculo,
            OffsetDateTime expiraEm) {
        if (organizacaoId == null || eventoId == null || reservaInventarioId == null
                || atorId == null) {
            throw new IllegalArgumentException(
                    "Organizacao, evento, reserva e ator sao obrigatorios.");
        }
        if (expiraEm == null || !expiraEm.toInstant()
                .isAfter(OffsetDateTime.now().toInstant())) {
            throw new IllegalArgumentException("A expiracao do pedido deve estar no futuro.");
        }
        Pedido pedido = new Pedido();
        pedido.organizacaoId = organizacaoId;
        pedido.eventoId = eventoId;
        pedido.reservaInventarioId = reservaInventarioId;
        pedido.atorId = atorId;
        pedido.numero = numero(numero);
        pedido.canal = codigo(canal, "canal", 30);
        pedido.placaVeiculo = placa(placaVeiculo);
        pedido.status = StatusPedido.CRIADO;
        pedido.subtotal = BigDecimal.ZERO.setScale(2);
        pedido.desconto = BigDecimal.ZERO.setScale(2);
        pedido.taxa = BigDecimal.ZERO.setScale(2);
        pedido.total = BigDecimal.ZERO.setScale(2);
        pedido.expiraEm = expiraEm;
        pedido.comprador = CompradorPedido.criar(
                pedido, nomeComprador, emailComprador, documentoComprador);
        return pedido;
    }

    public void adicionarItem(ItemPedido item) {
        if (status != StatusPedido.CRIADO || item == null || item.getPedido() != this) {
            throw new IllegalStateException("O item nao pode ser adicionado ao pedido.");
        }
        if (moeda != null && !moeda.equals(item.getMoeda())) {
            throw new IllegalArgumentException("Todos os itens devem usar a mesma moeda.");
        }
        itens.add(item);
        moeda = item.getMoeda();
        subtotal = subtotal.add(item.getTotal());
        total = subtotal.subtract(desconto).add(taxa);
    }

    public void aguardarPagamento() {
        if (status != StatusPedido.CRIADO || itens.isEmpty()) {
            throw new IllegalStateException(
                    "Somente pedido criado e com itens pode aguardar pagamento.");
        }
        status = StatusPedido.AGUARDANDO_PAGAMENTO;
    }

    public void confirmar() {
        if (status != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new IllegalStateException(
                    "Somente pedido aguardando pagamento pode ser confirmado.");
        }
        status = StatusPedido.CONFIRMADO;
    }

    public void atender() {
        if (status != StatusPedido.CONFIRMADO) {
            throw new IllegalStateException(
                    "Somente pedido confirmado pode ser atendido.");
        }
        status = StatusPedido.ATENDIDO;
    }

    public boolean expirar(OffsetDateTime instante) {
        if (status == StatusPedido.EXPIRADO) return false;
        if (status != StatusPedido.CRIADO && status != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new IllegalStateException("O estado do pedido nao permite expiracao.");
        }
        if (instante == null || instante.toInstant().isBefore(expiraEm.toInstant())) {
            return false;
        }
        status = StatusPedido.EXPIRADO;
        finalizadoEm = instante;
        return true;
    }

    public List<ItemPedido> getItens() {
        return Collections.unmodifiableList(itens);
    }

    private static String numero(String valor) {
        String numero = codigo(valor, "numero", 40);
        if (!numero.matches("PED-[A-F0-9]{32}")) {
            throw new IllegalArgumentException("O numero do pedido e invalido.");
        }
        return numero;
    }

    private static String placa(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String placa = valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!placa.matches("[A-Z0-9]{7}")) {
            throw new IllegalArgumentException("A placa do veiculo e invalida.");
        }
        return placa;
    }

    private static String codigo(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O " + campo + " e obrigatorio.");
        }
        String codigo = valor.trim().toUpperCase(Locale.ROOT);
        if (codigo.length() > limite
                || !codigo.matches("[A-Z][A-Z0-9_-]{0," + (limite - 1) + "}")) {
            throw new IllegalArgumentException("O " + campo + " e invalido.");
        }
        return codigo;
    }
}
