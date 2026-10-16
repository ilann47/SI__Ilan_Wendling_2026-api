package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados adicionais: cliente e valores comerciais são sempre resolvidos pela OS. */
public record GerarNotaOrdemServicoRequest(
        @NotBlank @Size(max = 20) String numero,
        @Size(max = 10) String modelo,
        @Size(max = 10) String serie,
        LocalDate dataEmissao,
        Long condicaoPagamentoId,
        Long formaPagamentoId,
        @PositiveOrZero @DecimalMax("100.00") BigDecimal aliquotaIss) {
}
