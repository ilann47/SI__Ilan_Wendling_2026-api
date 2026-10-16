package com.kaneko.estacionamento.estoque.dto;

import com.kaneko.estacionamento.estoque.MovimentoEstoqueView;
import com.kaneko.estacionamento.estoque.TipoMovimentoEstoque;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record MovimentoEstoqueResponse(
        Long id,
        Long produtoId,
        String produto,
        Long localEstoqueId,
        String localEstoque,
        Long saldoEstoqueId,
        TipoMovimentoEstoque tipo,
        BigDecimal delta,
        BigDecimal saldoAnterior,
        BigDecimal saldoPosterior,
        BigDecimal custoUnitario,
        String origemTipo,
        String origemChave,
        Long compensadoId,
        Long atorId,
        String atorNome,
        String motivo,
        OffsetDateTime ocorridoEm,
        OffsetDateTime createdAt
) {
    public static MovimentoEstoqueResponse from(MovimentoEstoqueView movimento) {
        return new MovimentoEstoqueResponse(movimento.getId(), movimento.getProdutoId(),
                movimento.getProduto(), movimento.getLocalEstoqueId(),
                movimento.getLocalEstoque(), movimento.getSaldoEstoqueId(),
                TipoMovimentoEstoque.valueOf(movimento.getTipo()), movimento.getDelta(),
                movimento.getSaldoAnterior(), movimento.getSaldoPosterior(),
                movimento.getCustoUnitario(), movimento.getOrigemTipo(),
                movimento.getOrigemChave(), movimento.getCompensadoId(),
                movimento.getAtorId(), movimento.getAtorNome(), movimento.getMotivo(),
                movimento.getOcorridoEm().atOffset(ZoneOffset.UTC),
                movimento.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
