package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.Colaborador;
import com.kaneko.estacionamento.patio.StatusColaborador;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um colaborador, com o patio resumido.
 */
public record ColaboradorResponse(
        Long id,
        Long patioId,
        String patioNome,
        String nome,
        String email,
        PerfilUsuario perfil,
        StatusColaborador status,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ColaboradorResponse from(Colaborador c) {
        return new ColaboradorResponse(
                c.getId(),
                c.getPatio() != null ? c.getPatio().getId() : null,
                c.getPatio() != null ? c.getPatio().getNome() : null,
                c.getNome(),
                c.getEmail(),
                c.getPerfil(),
                c.getStatus(),
                c.getAtivo(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
