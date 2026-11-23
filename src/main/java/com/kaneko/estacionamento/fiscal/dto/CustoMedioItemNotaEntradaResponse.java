package com.kaneko.estacionamento.fiscal.dto;

import java.math.BigDecimal;

/** Memoria de calculo do custo medio ponderado de um item da entrada. */
public record CustoMedioItemNotaEntradaResponse(
        Integer sequencia,
        Long produtoId,
        BigDecimal saldoAnterior,
        BigDecimal custoMedioAnterior,
        BigDecimal quantidadeEntrada,
        BigDecimal custoUnitarioEntrada,
        BigDecimal custoMedioPosterior,
        boolean aplicaAoEstoque,
        boolean projecao
) {
}
