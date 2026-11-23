package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.TipoVeiculo;
import com.kaneko.estacionamento.patio.Veiculo;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Veiculo, com o cliente (dono) resumido quando
 * houver.
 */
public record VeiculoResponse(
        Long id,
        Long organizacaoId,
        Long version,
        String placa,
        String modelo,
        String cor,
        TipoVeiculo tipo,
        Long clienteId,
        String clienteNome,
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static VeiculoResponse from(Veiculo v) {
        return new VeiculoResponse(
                v.getId(),
                v.getOrganizacaoId(),
                v.getVersion(),
                v.getPlaca(),
                v.getModelo(),
                v.getCor(),
                v.getTipo(),
                v.getCliente() != null ? v.getCliente().getId() : null,
                v.getCliente() != null ? v.getCliente().getNome() : null,
                v.getObservacao(),
                v.getAtivo(),
                v.getCreatedAt(),
                v.getUpdatedAt());
    }
}
