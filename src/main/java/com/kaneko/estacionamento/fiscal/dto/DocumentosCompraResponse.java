package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.compras.dto.RecebimentoCompraResponse;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
import java.util.List;

/** Null significa seção sem permissão; uma lista vazia significa consulta autorizada sem registros. */
public record DocumentosCompraResponse(
        List<RecebimentoCompraResponse> recebimentos,
        List<NotaEntradaResponse> notas,
        List<ContaPagarResponse> contasPagar,
        List<MovimentoEstoqueResponse> movimentosEstoque) { }
