package com.kaneko.estacionamento.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.AssertTrue;

@Getter @Setter @NoArgsConstructor
public class ConcederPapelRequest {
    @NotBlank private String roleCode;
    private OffsetDateTime validFrom;
    private OffsetDateTime validUntil;

    @AssertTrue(message = "validUntil deve ser posterior a validFrom.")
    public boolean isVigenciaValida() {
        return validUntil == null || validFrom == null || validUntil.isAfter(validFrom);
    }
}
