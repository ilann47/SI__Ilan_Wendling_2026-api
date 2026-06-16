package com.kaneko.estacionamento.pagamento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dados de entrada para criacao ou alteracao de uma condicao de pagamento,
 * incluindo (opcionalmente) o detalhamento das parcelas. Apenas {@code nome} e
 * {@code numeroParcelas} sao obrigatorios; os demais campos assumem zero quando
 * omitidos.
 */
public record CondicaoPagamentoRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        @NotNull(message = "O número de parcelas é obrigatório.")
        @Min(value = 1, message = "O número de parcelas deve ser maior ou igual a 1.")
        Integer numeroParcelas,

        @PositiveOrZero(message = "Os dias da primeira parcela não podem ser negativos.")
        Integer diasPrimeiraParcela,

        @PositiveOrZero(message = "Os dias entre parcelas não podem ser negativos.")
        Integer diasEntreParcelas,

        @PositiveOrZero(message = "O percentual de juros não pode ser negativo.")
        BigDecimal percentualJuros,

        @PositiveOrZero(message = "O percentual de multa não pode ser negativo.")
        BigDecimal percentualMulta,

        @PositiveOrZero(message = "O percentual de desconto não pode ser negativo.")
        @DecimalMax(value = "100.00", message = "O percentual de desconto não pode exceder 100.")
        BigDecimal percentualDesconto,

        Boolean ativo,

        @Valid
        List<ParcelaRequest> parcelas
) {
}
