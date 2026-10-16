package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Somente informações documentais: os valores comerciais vêm do pedido. */
public record GerarNotaVendaRequest(
        @NotBlank @Size(max = 20) String numero,
        @Size(max = 10) String modelo,
        @Size(max = 10) String serie,
        LocalDate dataEmissao,
        LocalDate dataSaida) {}
