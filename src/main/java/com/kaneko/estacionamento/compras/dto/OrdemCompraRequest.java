package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.TipoFreteOrdemCompra;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrdemCompraRequest(
        @NotNull Long fornecedorId,
        @NotBlank @Size(max = 60) String numeroNota,
        @NotBlank @Size(max = 20) String serieNota,
        @NotBlank @Size(max = 10) String modeloNota,
        LocalDate dataEmissao,
        LocalDate previsaoEntrega,
        @Pattern(regexp = "[A-Za-z]{3}") String moeda,
        Long condicaoPagamentoId,
        Long transportadoraId,
        Long localEstoqueEntregaId,
        TipoFreteOrdemCompra tipoFrete,
        @Size(max = 60) String referenciaFornecedor,
        @PositiveOrZero BigDecimal valorFrete,
        @PositiveOrZero BigDecimal valorSeguro,
        @PositiveOrZero BigDecimal outrasDespesas,
        @PositiveOrZero @DecimalMax(value = "0.00",
                message = "Desconto geral nao e permitido; informe o desconto em cada item.")
        BigDecimal valorDesconto,
        @Size(max = 500) String observacao,
        @Size(max = 1000) String observacaoInterna,
        @NotEmpty @Valid List<ItemOrdemCompraRequest> itens) {
}
