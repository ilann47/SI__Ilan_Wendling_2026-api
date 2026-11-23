package com.kaneko.estacionamento.membership.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.usuario.PerfilUsuario;

public record MeResponse(
        Long id,
        String name,
        String login,
        String email,
        PerfilUsuario legacyProfile,
        OrganizacaoAtivaResponse activeOrganization) {

    public static MeResponse from(UsuarioAutenticado principal) {
        OrganizacaoAtivaResponse contexto = principal.possuiOrganizacaoAtiva()
                ? new OrganizacaoAtivaResponse(
                        principal.organizacaoId(), principal.membershipId(), principal.membershipVersion())
                : null;
        return new MeResponse(
                principal.userId(), principal.nome(), principal.login(), principal.email(),
                principal.perfilLegado(), contexto);
    }

    /** Contrato publico em portugues consumido pelo onboarding atual. */
    @JsonProperty("nome")
    public String nome() {
        return name;
    }

    /** Perfil global; nunca representa o papel contextual da Membership. */
    @JsonProperty("perfil")
    public PerfilUsuario perfil() {
        return legacyProfile;
    }
}
