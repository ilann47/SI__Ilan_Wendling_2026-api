package com.kaneko.estacionamento.credencial;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credencial_acesso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredencialAcesso extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "patio_id", nullable = false, updatable = false)
    private Long patioId;
    @Column(name = "pedido_id", nullable = false, updatable = false)
    private Long pedidoId;
    @Column(name = "item_pedido_id", nullable = false, updatable = false)
    private Long itemPedidoId;
    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;
    @Column(name = "codigo_publico", nullable = false, updatable = false,
            length = 36)
    private String codigoPublico;
    @Column(name = "numero_unidade", nullable = false, updatable = false)
    private Integer numeroUnidade;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private OrigemCredencial origem;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private CategoriaProduto categoria;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private DireitoProduto direito;
    @Column(name = "inicio_validade", nullable = false, updatable = false)
    private OffsetDateTime inicioValidade;
    @Column(name = "fim_validade", nullable = false, updatable = false)
    private OffsetDateTime fimValidade;
    @Enumerated(EnumType.STRING)
    @Column(name = "meio_preferido", nullable = false, updatable = false,
            length = 20)
    private MeioPreferidoCredencial meioPreferido;
    @Column(name = "placa_veiculo", updatable = false, length = 7)
    private String placaVeiculo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusCredencial status;
    @Column(name = "qr_versao", nullable = false)
    private Integer qrVersao;
    @Version
    @Column(nullable = false)
    private Long version;

    public static CredencialAcesso criar(
            Long organizacaoId, Long eventoId, Long patioId, Long pedidoId,
            Long itemPedidoId, Long atorId, String codigoPublico,
            Integer numeroUnidade, OrigemCredencial origem,
            CategoriaProduto categoria, DireitoProduto direito,
            OffsetDateTime inicioValidade, OffsetDateTime fimValidade,
            MeioPreferidoCredencial meioPreferido, String placaVeiculo) {
        if (organizacaoId == null || eventoId == null || patioId == null
                || pedidoId == null || itemPedidoId == null || atorId == null
                || origem == null || categoria == null || direito == null
                || meioPreferido == null || numeroUnidade == null
                || numeroUnidade < 1) {
            throw new IllegalArgumentException(
                    "O escopo e o direito da credencial sao obrigatorios.");
        }
        if (codigoPublico == null
                || !codigoPublico.matches("CRD-[A-F0-9]{32}")) {
            throw new IllegalArgumentException("O codigo publico da credencial e invalido.");
        }
        if (inicioValidade == null || fimValidade == null
                || !fimValidade.toInstant().isAfter(inicioValidade.toInstant())) {
            throw new IllegalArgumentException("A validade da credencial e invalida.");
        }
        CredencialAcesso credencial = new CredencialAcesso();
        credencial.organizacaoId = organizacaoId;
        credencial.eventoId = eventoId;
        credencial.patioId = patioId;
        credencial.pedidoId = pedidoId;
        credencial.itemPedidoId = itemPedidoId;
        credencial.atorId = atorId;
        credencial.codigoPublico = codigoPublico;
        credencial.numeroUnidade = numeroUnidade;
        credencial.origem = origem;
        credencial.categoria = categoria;
        credencial.direito = direito;
        credencial.inicioValidade = inicioValidade;
        credencial.fimValidade = fimValidade;
        credencial.meioPreferido = meioPreferido;
        credencial.placaVeiculo = placa(placaVeiculo);
        credencial.status = StatusCredencial.PENDENTE;
        credencial.qrVersao = 0;
        return credencial;
    }

    public void ativar() {
        if (status != StatusCredencial.PENDENTE) {
            throw new IllegalStateException("Somente credencial pendente pode ser ativada.");
        }
        status = StatusCredencial.ATIVA;
    }

    public int registrarRepresentacaoQr(OffsetDateTime emitidoEm) {
        if (status != StatusCredencial.ATIVA) {
            throw new IllegalStateException(
                    "Somente credencial ativa pode gerar QR Code.");
        }
        if (meioPreferido != MeioPreferidoCredencial.QR) {
            throw new IllegalStateException(
                    "A credencial nao permite representacao QR.");
        }
        if (emitidoEm == null
                || !fimValidade.toInstant().isAfter(emitidoEm.toInstant())) {
            throw new IllegalStateException(
                    "A credencial esta expirada para gerar QR Code.");
        }
        qrVersao++;
        return qrVersao;
    }

    private static String placa(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String placa = valor.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        if (!placa.matches("[A-Z0-9]{7}")) {
            throw new IllegalArgumentException("A placa da credencial e invalida.");
        }
        return placa;
    }
}
