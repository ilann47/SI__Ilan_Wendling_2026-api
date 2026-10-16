package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.compras.OrdemCompra;
import com.kaneko.estacionamento.compras.StatusOrdemCompra;

/** Referencia comercial minima da ordem que originou a nota de entrada. */
public record OrdemCompraVinculadaResponse(
        Long id,
        String numero,
        StatusOrdemCompra status
) {

    public static OrdemCompraVinculadaResponse from(OrdemCompra ordem) {
        return new OrdemCompraVinculadaResponse(
                ordem.getId(), ordem.getNumeroNota(), ordem.getStatus());
    }
}
