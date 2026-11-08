package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import java.util.List;

public record NotaSaidaDetalheResponse(
        NotaSaidaResponse nota,
        List<ParcelaNotaEntradaResponse> parcelas,
        List<ContaReceberResponse> contasReceber,
        List<MovimentoEstoqueResponse> movimentosEstoque
) { }
