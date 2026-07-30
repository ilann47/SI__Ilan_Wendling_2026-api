package com.kaneko.estacionamento.evento.dto;

import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.StatusEvento;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record EventoResponse(
        Long id,
        Long organizationId,
        Long venueId,
        String name,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String timeZone,
        StatusEvento status,
        String externalId,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ChecklistConfiguracaoEventoResponse configurationChecklist) {

    public static EventoResponse from(Evento evento) {
        ZoneId fuso = ZoneId.of(evento.getFusoHorario());
        return new EventoResponse(
                evento.getId(), evento.getOrganizacaoId(), evento.getVenueId(), evento.getNome(),
                evento.getInicio().atZoneSameInstant(fuso).toOffsetDateTime(),
                evento.getFim().atZoneSameInstant(fuso).toOffsetDateTime(),
                evento.getFusoHorario(), evento.getStatus(), evento.getExternalId(), evento.getVersion(),
                evento.getCreatedAt(), evento.getUpdatedAt(), ChecklistConfiguracaoEventoResponse.inicial());
    }
}
