package com.kaneko.estacionamento.conveniencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada para criacao ou alteracao de um Produto. Marca e unidade de
 * medida sao obrigatorias; valores e estoque assumem zero quando omitidos.
 */
public record ProdutoRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 20) String codigoBarras,
        @Size(max = 10) String referencia,

        @NotNull(message = "A marca é obrigatória.")
        Long marcaId,

        @NotNull(message = "A unidade de medida é obrigatória.")
        Long unidadeMedidaId,

        Long categoriaId,

        @PositiveOrZero(message = "O valor de compra não pode ser negativo.")
        BigDecimal valorCompra,

        @PositiveOrZero(message = "O valor de venda não pode ser negativo.")
        BigDecimal valorVenda,

        @PositiveOrZero(message = "O percentual de lucro não pode ser negativo.")
        BigDecimal percentualLucro,

        @PositiveOrZero(message = "A quantidade não pode ser negativa.")
        BigDecimal quantidade,

        @PositiveOrZero(message = "A quantidade mínima não pode ser negativa.")
        BigDecimal quantidadeMinima,

        @Size(max = 255) String descricao,
        @Size(max = 255) String observacao,

        Boolean ativo
) {
}
