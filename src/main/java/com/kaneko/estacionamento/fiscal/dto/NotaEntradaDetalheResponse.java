package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.compras.dto.RecebimentoCompraResponse;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
import java.util.List;

/**
 * Visao composta da nota de entrada para consulta operacional, sem alterar o
 * contrato enxuto usado pelas listagens.
 */
public record NotaEntradaDetalheResponse(
        NotaEntradaResponse nota,
        OrdemCompraVinculadaResponse ordemCompra,
        RecebimentoCompraResponse recebimento,
        List<ContaPagarResponse> contasPagar,
        List<MovimentoEstoqueResponse> movimentosEstoque
) {
}
