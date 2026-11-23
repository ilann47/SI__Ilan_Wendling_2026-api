package com.kaneko.estacionamento.credencial.dto;

import java.time.OffsetDateTime;

public record QrCodeResponse(
        Long id,
        Long credentialId,
        Integer representationVersion,
        String token,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        Long credentialVersion
) {
}
