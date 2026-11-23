package com.kaneko.estacionamento.fiscal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record DocumentosOrdemServicoResponse(NotaServicoResponse nota,
        List<ContaReceberResponse> contas, NfsePatioPendente nfsePatio) {
    public record NfsePatioPendente(long movimentacaoId, String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor, OffsetDateTime criadoEm) {}
}
