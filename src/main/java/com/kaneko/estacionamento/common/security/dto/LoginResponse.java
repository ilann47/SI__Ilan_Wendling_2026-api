package com.kaneko.estacionamento.common.security.dto;

import com.kaneko.estacionamento.usuario.PerfilUsuario;

/**
 * Resposta de um login bem-sucedido, com o token de acesso.
 */
public record LoginResponse(
        String token,
        String tipo,
        String login,
        PerfilUsuario perfil
) {
}
