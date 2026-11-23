package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.Mensalista;
import com.kaneko.estacionamento.patio.StatusMensalista;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um contrato de Mensalista, com cliente e veiculo
 * resumidos.
 */
public record MensalistaResponse(
        Long id,
        Long organizacaoId,
        Long version,
        Long clienteId,
        String clienteNome,
        Long veiculoId,
        String veiculoPlaca,
        BigDecimal valorMensal,
        LocalDate dataInicio,
        LocalDate dataFim,
        StatusMensalista status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static MensalistaResponse from(Mensalista m) {
        return new MensalistaResponse(
                m.getId(),
                m.getOrganizacaoId(),
                m.getVersion(),
                m.getCliente().getId(),
                m.getCliente().getNome(),
                m.getVeiculo().getId(),
                m.getVeiculo().getPlaca(),
                m.getValorMensal(),
                m.getDataInicio(),
                m.getDataFim(),
                m.getStatus(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
