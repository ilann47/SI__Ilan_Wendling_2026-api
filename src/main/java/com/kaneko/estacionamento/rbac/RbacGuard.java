package com.kaneko.estacionamento.rbac;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.membership.MembroOrganizacaoRepository;
import com.kaneko.estacionamento.membership.StatusMembroOrganizacao;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("rbacGuard")
public class RbacGuard {
    private final boolean enabled; private final RbacService rbac; private final MembroOrganizacaoRepository memberships;
    public RbacGuard(@Value("${app.security.enabled:true}") boolean enabled, RbacService rbac,
                     MembroOrganizacaoRepository memberships) {
        this.enabled = enabled; this.rbac = rbac; this.memberships = memberships;
    }
    public boolean platformAdmin(Authentication auth) {
        if (!enabled) return true;
        return principal(auth) != null && principal(auth).perfilLegado() == PerfilUsuario.ADMIN
                && !principal(auth).possuiOrganizacaoAtiva();
    }
    public boolean hasPermission(Long orgId, String permission, Authentication auth) {
        if (!enabled) return true;
        UsuarioAutenticado p = principal(auth);
        if (p == null || !p.possuiOrganizacaoAtiva()) return false;
        if (!orgId.equals(p.organizacaoId())) {
            throw new RecursoNaoEncontradoException("Organizacao", orgId);
        }
        return rbac.permissoes(p.membershipId(), orgId).contains(permission);
    }
    public boolean hasActiveContext(Authentication auth) {
        if (!enabled) return true;
        UsuarioAutenticado p = principal(auth);
        return p != null && p.possuiOrganizacaoAtiva();
    }
    public boolean canReadOrganization(Long orgId, Authentication auth) {
        return platformAdmin(auth) || hasPermission(orgId, "organizations:admin", auth);
    }
    public boolean canProvisionMembership(Long orgId, Authentication auth) {
        if (hasPermission(orgId, "users:invite", auth)) return true;
        return platformAdmin(auth) && memberships.countByOrganizacaoIdAndStatusNot(
                orgId, StatusMembroOrganizacao.ENCERRADO) == 0;
    }
    private static UsuarioAutenticado principal(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado p ? p : null;
    }
}
