package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados de entrada para a nota de servico. O valor do ISS e o total sao
 * calculados pelo servico a partir do valor do servico, da aliquota e do desconto.
 */
public record NotaServicoRequest(

        @NotBlank(message = "O número da nota é obrigatório.")
        @Size(max = 20) String numero,

        @Size(max = 10) String modelo,
        @Size(max = 10) String serie,

        @NotNull(message = "O cliente é obrigatório.")
        Long clienteId,

        Long servicoId,
        Long movimentacaoId,
        Long mensalistaId,
        Long ordemServicoId,
        Long condicaoPagamentoId,
        Long formaPagamentoId,

        LocalDate dataEmissao,

        @PositiveOrZero(message = "O valor do serviço não pode ser negativo.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorServico,

        @PositiveOrZero(message = "A alíquota de ISS não pode ser negativa.")
        @DecimalMax(value = "100.00", message = "A alíquota de ISS não pode exceder 100.")
        @Digits(integer = 3, fraction = 6)
        BigDecimal aliquotaIss,

        @PositiveOrZero(message = "O desconto não pode ser negativo.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorDesconto,

        @Size(max = 255) String observacao,
        @Size(max = 100) List<@NotNull @Valid ItemNotaServicoRequest> itens
) {
}
