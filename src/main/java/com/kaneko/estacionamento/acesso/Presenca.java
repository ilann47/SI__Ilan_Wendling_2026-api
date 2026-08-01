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
@Table(name = "presenca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Presenca extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "patio_id", nullable = false, updatable = false)
    private Long patioId;
    @Column(name = "credencial_id", nullable = false, updatable = false)
    private Long credencialId;
    @Column(name = "tentativa_checkin_id", nullable = false, updatable = false)
    private Long tentativaCheckinId;
    @Column(name = "checkin_em", nullable = false, updatable = false)
    private OffsetDateTime checkinEm;
    @Column(name = "checkout_em")
    private OffsetDateTime checkoutEm;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPresenca status;
    @Version
    @Column(nullable = false)
    private Long version;

    public static Presenca entrar(
            Long organizacaoId, Long eventoId, Long patioId,
            Long credencialId, Long tentativaCheckinId,
            OffsetDateTime instante) {
        if (organizacaoId == null || eventoId == null || patioId == null
                || credencialId == null || tentativaCheckinId == null
                || instante == null) {
            throw new IllegalArgumentException(
                    "O escopo e a origem da presenca sao obrigatorios.");
        }
        Presenca presenca = new Presenca();
        presenca.organizacaoId = organizacaoId;
        presenca.eventoId = eventoId;
        presenca.patioId = patioId;
        presenca.credencialId = credencialId;
        presenca.tentativaCheckinId = tentativaCheckinId;
        presenca.checkinEm = instante;
        presenca.status = StatusPresenca.DENTRO;
        return presenca;
    }
}
