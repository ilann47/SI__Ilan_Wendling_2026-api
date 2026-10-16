package com.kaneko.estacionamento.instalacoes.dto;

import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import com.kaneko.estacionamento.instalacoes.Setor;
import java.time.OffsetDateTime;
import java.util.List;

public record SetorResponse(
        Long id,
        Long organizationId,
        Long parkingFacilityId,
        String code,
        String name,
        Integer capacity,
        List<CategoriaSetor> categories,
        Boolean active,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static SetorResponse from(Setor setor) {
        return new SetorResponse(
                setor.getId(), setor.getOrganizacaoId(), setor.getPatioId(),
                setor.getCodigo(), setor.getNome(), setor.getCapacidade(),
                setor.categorias(), setor.getAtivo(), setor.getVersion(),
                setor.getCreatedAt(), setor.getUpdatedAt());
    }
}
