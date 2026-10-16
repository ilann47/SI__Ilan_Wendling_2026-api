package com.kaneko.estacionamento.credencial.dto;

import com.kaneko.estacionamento.credencial.CredencialAcesso;
import com.kaneko.estacionamento.credencial.MeioPreferidoCredencial;
import com.kaneko.estacionamento.credencial.OrigemCredencial;
import com.kaneko.estacionamento.credencial.StatusCredencial;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import java.time.OffsetDateTime;

public record CredencialAcessoResponse(
        Long id,
        Long organizationId,
        Long eventId,
        Long parkingFacilityId,
        Long orderId,
        Long orderItemId,
        String publicCode,
        Integer unitNumber,
        OrigemCredencial origin,
        CategoriaProduto category,
        DireitoProduto right,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        MeioPreferidoCredencial preferredMedium,
        String vehiclePlate,
        StatusCredencial status,
        Integer qrVersion,
        OffsetDateTime blockedAt,
        String blockingReason,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static CredencialAcessoResponse from(CredencialAcesso credencial) {
        return new CredencialAcessoResponse(
                credencial.getId(), credencial.getOrganizacaoId(),
                credencial.getEventoId(), credencial.getPatioId(),
                credencial.getPedidoId(), credencial.getItemPedidoId(),
                credencial.getCodigoPublico(), credencial.getNumeroUnidade(),
                credencial.getOrigem(), credencial.getCategoria(),
                credencial.getDireito(), credencial.getInicioValidade(),
                credencial.getFimValidade(), credencial.getMeioPreferido(),
                credencial.getPlacaVeiculo(), credencial.getStatus(),
                credencial.getQrVersao(), credencial.getBloqueadaEm(),
                credencial.getMotivoBloqueio(), credencial.getVersion(),
                credencial.getCreatedAt(),
                credencial.getUpdatedAt());
    }
}
