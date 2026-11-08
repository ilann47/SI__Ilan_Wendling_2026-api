package com.kaneko.estacionamento.fiscal.dto;

import java.math.BigDecimal;
import java.util.List;

public record PreviaNotaEntradaResponse(NotaEntradaResponse nota,
        List<ParcelaNotaEntradaResponse> parcelas, BigDecimal valorCustoTotal,
        BigDecimal valorObrigacao, BigDecimal diferencaDescontoGeral,
        List<CustoMedioItemNotaEntradaResponse> custosMedios) { }
