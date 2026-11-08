package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import java.util.List;

public record NotaServicoDetalheResponse(NotaServicoResponse nota, List<ContaReceberResponse> contasReceber) {
}
