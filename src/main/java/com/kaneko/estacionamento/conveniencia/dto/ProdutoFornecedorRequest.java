package com.kaneko.estacionamento.conveniencia.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada do vinculo produto-fornecedor.
 */
public record ProdutoFornecedorRequest(

        @NotNull(message = "O produto é obrigatório.")
        Long produtoId,

        @NotNull(message = "O fornecedor é obrigatório.")
        Long fornecedorId,

        @Size(max = 50) String codigoProd,

        @PositiveOrZero(message = "O custo não pode ser negativo.")
        BigDecimal custo
) {
}
