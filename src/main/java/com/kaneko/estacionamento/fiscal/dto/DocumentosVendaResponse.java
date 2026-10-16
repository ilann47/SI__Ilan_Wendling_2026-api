package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import java.util.List;

/** Seção sem permissão é null; coleção vazia significa consulta autorizada sem registros. */
public record DocumentosVendaResponse(NotaSaidaResponse nota,
        List<ContaReceberResponse> contas, List<MovimentoEstoqueResponse> movimentos) {}
