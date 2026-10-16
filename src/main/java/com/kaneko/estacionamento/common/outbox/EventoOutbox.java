package com.kaneko.estacionamento.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evento_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoOutbox {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 80)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;
    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private Long aggregateVersion;
    @Column(name = "correlation_id", nullable = false, updatable = false, length = 100)
    private String correlationId;
    @Column(name = "causation_id", updatable = false)
    private UUID causationId;
    @Column(name = "schema_version", nullable = false, updatable = false)
    private Integer schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEventoOutbox status;
    @Column(nullable = false)
    private Integer tentativas;
    @Column(name = "disponivel_em", nullable = false)
    private OffsetDateTime disponivelEm;
    @Column(name = "publicado_em")
    private OffsetDateTime publicadoEm;
    @Column(name = "ultimo_erro", length = 1000)
    private String ultimoErro;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    static EventoOutbox criar(PublicarEventoOutboxCommand command, String correlationId) {
        if (command == null || command.organizacaoId() == null || command.aggregateId() == null
                || command.aggregateVersion() == null || command.aggregateVersion() < 0
                || command.schemaVersion() < 1 || command.payload() == null) {
            throw new IllegalArgumentException("O evento de outbox esta incompleto.");
        }
        EventoOutbox evento = new EventoOutbox();
        evento.eventId = UUID.randomUUID();
        evento.eventType = codigo(command.eventType(), "tipo do evento", 100);
        evento.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        evento.organizacaoId = command.organizacaoId();
        evento.aggregateType = codigo(command.aggregateType(), "tipo do agregado", 80);
        evento.aggregateId = command.aggregateId();
        evento.aggregateVersion = command.aggregateVersion();
        evento.correlationId = textoObrigatorio(correlationId, "correlationId", 100);
        evento.causationId = command.causationId();
        evento.schemaVersion = command.schemaVersion();
        evento.payload = Collections.unmodifiableMap(new LinkedHashMap<>(command.payload()));
        evento.status = StatusEventoOutbox.PENDENTE;
        evento.tentativas = 0;
        evento.disponivelEm = evento.occurredAt;
        return evento;
    }

    private static String codigo(String valor, String campo, int limite) {
        String codigo = textoObrigatorio(valor, campo, limite);
        if (!codigo.matches("[A-Za-z][A-Za-z0-9_]{2," + (limite - 1) + "}")) {
            throw new IllegalArgumentException("O " + campo + " e invalido.");
        }
        return codigo;
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException("O " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }
}
