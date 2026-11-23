package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.usuario.PerfilUsuario;
import java.security.Principal;

/** Snapshot imutavel da identidade e do contexto validados para a requisicao. */
public record UsuarioAutenticado(
        Long userId,
        String login,
        String nome,
        String email,
        PerfilUsuario perfilLegado,
        Long organizacaoId,
        Long membershipId,
        Long membershipVersion) implements Principal {

    @Override
    public String getName() {
        return login;
    }

    public boolean possuiOrganizacaoAtiva() {
        return organizacaoId != null && membershipId != null && membershipVersion != null;
    }
}
