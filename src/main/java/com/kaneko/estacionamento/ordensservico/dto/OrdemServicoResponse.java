package com.kaneko.estacionamento.ordensservico.dto;

import com.kaneko.estacionamento.ordensservico.OrdemServico;
import com.kaneko.estacionamento.ordensservico.StatusOrdemServico;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrdemServicoResponse(Long id, Long organizacaoId, Long version,
        String numero, StatusOrdemServico status, Long clienteId, String clienteNome,
        LocalDate dataAbertura, LocalDate previsaoConclusao, OffsetDateTime iniciadaEm,
        OffsetDateTime concluidaEm, String moeda, BigDecimal subtotal,
        BigDecimal valorDesconto, BigDecimal valorTotal, String observacao,
        String motivoCancelamento, List<ItemOrdemServicoResponse> itens,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static OrdemServicoResponse from(OrdemServico ordem) {
        return new OrdemServicoResponse(ordem.getId(), ordem.getOrganizacaoId(),
                ordem.getVersion(), ordem.getNumero(), ordem.getStatus(),
                ordem.getCliente().getId(), ordem.getCliente().getNome(),
                ordem.getDataAbertura(), ordem.getPrevisaoConclusao(), ordem.getIniciadaEm(),
                ordem.getConcluidaEm(), ordem.getMoeda(), ordem.getSubtotal(),
                ordem.getValorDesconto(), ordem.getValorTotal(), ordem.getObservacao(),
                ordem.getMotivoCancelamento(), ordem.getItens().stream()
                        .map(ItemOrdemServicoResponse::from).toList(),
                ordem.getCreatedAt(), ordem.getUpdatedAt());
    }
}
