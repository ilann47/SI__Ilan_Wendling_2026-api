package com.kaneko.estacionamento.usuario.dto;

import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um usuario. A senha (hash) nunca e exposta.
 */
public record UsuarioResponse(
        Long id,
        String nome,
        String login,
        String email,
        Long funcionarioId,
        PerfilUsuario perfil,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNome(),
                u.getLogin(),
                u.getEmail(),
                u.getFuncionario() != null ? u.getFuncionario().getId() : null,
                u.getPerfil(),
                u.getAtivo(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }
}
