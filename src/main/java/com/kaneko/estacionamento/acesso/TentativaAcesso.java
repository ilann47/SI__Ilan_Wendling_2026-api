package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tentativa_acesso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TentativaAcesso extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "patio_id", nullable = false, updatable = false)
    private Long patioId;
    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;
    @Column(name = "credencial_id")
    private Long credencialId;
    @Column(name = "representacao_qr_id")
    private Long representacaoQrId;
    @Column(nullable = false, updatable = false, length = 20)
    private String direcao;
    @Column(name = "origem_leitura", nullable = false, updatable = false, length = 20)
    private String origemLeitura;
    @Column(nullable = false, updatable = false, length = 80)
    private String faixa;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusTentativaAcesso status;
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private MotivoDecisaoAcesso motivo;
    @Column(name = "ocupacao_resultante")
    private Integer ocupacaoResultante;
    @Column(name = "recebido_em", nullable = false, updatable = false)
    private OffsetDateTime recebidoEm;
    @Column(name = "decidido_em")
    private OffsetDateTime decididoEm;
    @Version
    @Column(nullable = false)
    private Long version;

    public static TentativaAcesso receber(
            Long organizacaoId, Long eventoId, Long patioId, Long atorId,
            String faixa, OffsetDateTime recebidoEm) {
        if (organizacaoId == null || eventoId == null || patioId == null
                || atorId == null || recebidoEm == null) {
            throw new IllegalArgumentException(
                    "O escopo, ator e instante da tentativa sao obrigatorios.");
        }
        if (faixa == null || faixa.isBlank() || faixa.trim().length() > 80) {
            throw new IllegalArgumentException(
                    "A faixa deve ter entre 1 e 80 caracteres.");
        }
        TentativaAcesso tentativa = new TentativaAcesso();
        tentativa.organizacaoId = organizacaoId;
        tentativa.eventoId = eventoId;
        tentativa.patioId = patioId;
        tentativa.atorId = atorId;
        tentativa.direcao = "ENTRADA";
        tentativa.origemLeitura = "QR";
        tentativa.faixa = faixa.trim();
        tentativa.status = StatusTentativaAcesso.RECEBIDA;
        tentativa.recebidoEm = recebidoEm;
        return tentativa;
    }

    public void avaliar() {
        exigirStatus(StatusTentativaAcesso.RECEBIDA);
        status = StatusTentativaAcesso.AVALIANDO;
    }

    public void vincularCredencial(Long credencialId, Long representacaoQrId) {
        exigirStatus(StatusTentativaAcesso.AVALIANDO);
        if (credencialId == null || representacaoQrId == null) {
            throw new IllegalArgumentException(
                    "Credencial e representacao QR sao obrigatorias.");
        }
        this.credencialId = credencialId;
        this.representacaoQrId = representacaoQrId;
    }

    public void autorizar(
            Long credencialId, Long representacaoQrId,
            Integer ocupacaoResultante, OffsetDateTime instante) {
        vincularCredencial(credencialId, representacaoQrId);
        if (ocupacaoResultante == null
                || ocupacaoResultante < 1 || instante == null) {
            throw new IllegalArgumentException(
                    "Os efeitos e o instante da autorizacao sao obrigatorios.");
        }
        status = StatusTentativaAcesso.AUTORIZADA;
        motivo = MotivoDecisaoAcesso.AUTORIZADA;
        this.ocupacaoResultante = ocupacaoResultante;
        decididoEm = instante;
    }

    public void recusar(MotivoDecisaoAcesso motivo, OffsetDateTime instante) {
        exigirStatus(StatusTentativaAcesso.AVALIANDO);
        if (motivo == null || motivo == MotivoDecisaoAcesso.AUTORIZADA
                || instante == null) {
            throw new IllegalArgumentException(
                    "O motivo e o instante da recusa sao obrigatorios.");
        }
        this.motivo = motivo;
        decididoEm = instante;
        status = StatusTentativaAcesso.RECUSADA;
    }

    private void exigirStatus(StatusTentativaAcesso esperado) {
        if (status != esperado) {
            throw new IllegalStateException(
                    "A tentativa nao esta no estado " + esperado + ".");
        }
    }
}
