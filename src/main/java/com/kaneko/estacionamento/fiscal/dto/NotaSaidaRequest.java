package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.fiscal.TipoFrete;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados de entrada para a criacao de uma nota fiscal de saida com seus itens.
 * Os totais e rateios sao calculados pelo servico.
 */
public record NotaSaidaRequest(

        @NotBlank(message = "O número da nota é obrigatório.")
        @Size(max = 20) String numero,

        @Size(max = 10) String modelo,
        @Size(max = 10) String serie,

        @NotNull(message = "O cliente é obrigatório.")
        Long clienteId,

        Long condicaoPagamentoId,
        Long localEstoqueId,

        LocalDate dataEmissao,
        LocalDate dataSaida,

        TipoFrete tipoFrete,

        @PositiveOrZero BigDecimal valorFrete,
        @PositiveOrZero BigDecimal valorSeguro,
        @PositiveOrZero BigDecimal outrasDespesas,
        @PositiveOrZero BigDecimal valorDesconto,

        @Size(max = 255) String observacao,

        @NotEmpty(message = "A nota deve conter ao menos um item.")
        @Valid List<ItemNotaSaidaRequest> itens
) {
}
