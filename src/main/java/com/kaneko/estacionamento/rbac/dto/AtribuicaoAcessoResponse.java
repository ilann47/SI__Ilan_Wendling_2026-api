package com.kaneko.estacionamento.rbac.dto;

import com.kaneko.estacionamento.rbac.AtribuicaoAcesso;
import java.time.OffsetDateTime;

public record AtribuicaoAcessoResponse(Long id, Long membershipId, Long organizationId,
        String roleCode, String scopeType, Long scopeId, OffsetDateTime grantedAt,
        OffsetDateTime validFrom, OffsetDateTime validUntil,
        OffsetDateTime revokedAt, String revocationReason, Long version) {
    public static AtribuicaoAcessoResponse from(AtribuicaoAcesso a, String roleCode) {
        return new AtribuicaoAcessoResponse(a.getId(), a.getMembershipId(), a.getOrganizacaoId(),
                roleCode, a.getEscopoTipo(), a.getEscopoId(), a.getConcedidoEm(),
                a.getVigenteDe(), a.getVigenteAte(), a.getRevogadoEm(),
                a.getMotivoRevogacao(), a.getVersion());
    }
}
