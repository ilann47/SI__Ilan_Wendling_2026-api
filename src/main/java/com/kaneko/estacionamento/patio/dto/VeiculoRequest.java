package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.TipoVeiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de um Veiculo. Quando o tipo nao
 * e informado, assume-se CARRO. O {@code clienteId} e opcional (veiculo avulso
 * nao tem cliente).
 */
public record VeiculoRequest(

        @NotBlank(message = "A placa é obrigatória.")
        @Size(max = 10, message = "A placa deve ter no máximo 10 caracteres.")
        String placa,

        @Size(max = 60, message = "O modelo deve ter no máximo 60 caracteres.")
        String modelo,

        @Size(max = 30, message = "A cor deve ter no máximo 30 caracteres.")
        String cor,

        TipoVeiculo tipo,

        Long clienteId,

        @Size(max = 255, message = "A observação deve ter no máximo 255 caracteres.")
        String observacao,

        Boolean ativo
) {
}
