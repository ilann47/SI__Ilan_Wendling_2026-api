package com.kaneko.estacionamento.fiscal.dto;

import java.math.BigDecimal;

public record ItemNotaServicoResponse(int sequencia, Long servicoId, String descricao,
        BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorDesconto, BigDecimal valorTotal) {
}
