package com.kaneko.estacionamento.credencial;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "representacao_qr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepresentacaoQr extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "credencial_id", nullable = false, updatable = false)
    private Long credencialId;
    @Column(name = "numero_versao", nullable = false, updatable = false)
    private Integer numeroVersao;
    @Column(name = "token_hash", nullable = false, updatable = false,
            length = 64)
    private String tokenHash;
    @Column(name = "emitido_em", nullable = false, updatable = false)
    private OffsetDateTime emitidoEm;
    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;
    @Column(name = "revogada_em")
    private OffsetDateTime revogadaEm;
    @Version
    @Column(nullable = false)
    private Long version;

    public static RepresentacaoQr criar(
            Long organizacaoId, Long credencialId, Integer numeroVersao,
            String tokenHash, OffsetDateTime emitidoEm,
            OffsetDateTime expiraEm) {
        if (organizacaoId == null || credencialId == null
                || numeroVersao == null || numeroVersao < 1) {
            throw new IllegalArgumentException(
                    "O escopo e a versao da representacao QR sao obrigatorios.");
        }
        if (tokenHash == null || !tokenHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(
                    "O hash da representacao QR e invalido.");
        }
        if (emitidoEm == null || expiraEm == null
                || !expiraEm.toInstant().isAfter(emitidoEm.toInstant())) {
            throw new IllegalArgumentException(
                    "A validade da representacao QR e invalida.");
        }
        RepresentacaoQr representacao = new RepresentacaoQr();
        representacao.organizacaoId = organizacaoId;
        representacao.credencialId = credencialId;
        representacao.numeroVersao = numeroVersao;
        representacao.tokenHash = tokenHash;
        representacao.emitidoEm = emitidoEm;
        representacao.expiraEm = expiraEm;
        return representacao;
    }
}
