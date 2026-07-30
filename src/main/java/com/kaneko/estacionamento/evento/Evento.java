package com.kaneko.estacionamento.evento;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evento extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "venue_id", nullable = false, updatable = false)
    private Long venueId;
    @Column(nullable = false, length = 180)
    private String nome;
    @Column(nullable = false)
    private OffsetDateTime inicio;
    @Column(nullable = false)
    private OffsetDateTime fim;
    @Column(name = "fuso_horario", nullable = false, length = 60)
    private String fusoHorario;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusEvento status;
    @Column(name = "external_id", length = 160)
    private String externalId;
    @Version
    @Column(nullable = false)
    private Long version;

    public static Evento criar(
            Long organizacaoId, Long venueId, String nome,
            OffsetDateTime inicio, OffsetDateTime fim,
            String fusoHorario, String externalId) {
        if (organizacaoId == null || venueId == null) {
            throw new IllegalArgumentException("Organizacao e venue sao obrigatorios.");
        }
        if (inicio == null || fim == null || !fim.toInstant().isAfter(inicio.toInstant())) {
            throw new IllegalArgumentException("O fim do evento deve ser posterior ao inicio.");
        }
        Evento evento = new Evento();
        evento.organizacaoId = organizacaoId;
        evento.venueId = venueId;
        evento.nome = textoObrigatorio(nome, "nome", 180);
        evento.inicio = inicio;
        evento.fim = fim;
        evento.fusoHorario = validarFuso(fusoHorario);
        evento.externalId = textoOpcional(externalId, "externalId", 160);
        evento.status = StatusEvento.RASCUNHO;
        return evento;
    }

    public void configurar() {
        if (status != StatusEvento.RASCUNHO) {
            throw new IllegalStateException(
                    "Somente evento em rascunho pode ser configurado.");
        }
        status = StatusEvento.CONFIGURADO;
    }

    public void publicar() {
        if (status != StatusEvento.CONFIGURADO) {
            throw new IllegalStateException(
                    "Somente evento configurado pode ser publicado.");
        }
        status = StatusEvento.PUBLICADO;
    }

    static String validarFuso(String valor) {
        String fuso = textoObrigatorio(valor, "fuso horario", 60);
        try {
            return ZoneId.of(fuso).getId();
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("O fuso horario deve ser um identificador IANA valido.", ex);
        }
    }

    static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException("O campo " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }

    static String textoOpcional(String valor, String campo, int limite) {
        return valor == null || valor.isBlank() ? null : textoObrigatorio(valor, campo, limite);
    }
}
