package com.kaneko.estacionamento.usuario.dto;

import com.kaneko.estacionamento.usuario.PerfilUsuario;

/** Conta criada e credencial global emitida para iniciar o onboarding. */
public record CadastroContaResponse(
        Long id,
        String nome,
        String login,
        String email,
        String token,
        String tipo,
        PerfilUsuario perfil
) {
}
