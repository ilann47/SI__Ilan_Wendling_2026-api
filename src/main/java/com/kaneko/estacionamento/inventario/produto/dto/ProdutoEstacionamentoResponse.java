package com.kaneko.estacionamento.inventario.produto.dto;

import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamento;
import com.kaneko.estacionamento.inventario.produto.StatusProdutoEstacionamento;
import java.time.OffsetDateTime;
import java.util.List;

public record ProdutoEstacionamentoResponse(
        Long id,
        Long organizationId,
        Long eventId,
        Long parkingAllocationId,
        String name,
        CategoriaProduto category,
        DireitoProduto right,
        OffsetDateTime accessStartsAt,
        OffsetDateTime accessEndsAt,
        Integer quota,
        StatusProdutoEstacionamento status,
        List<String> benefits,
        List<String> restrictions,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProdutoEstacionamentoResponse from(ProdutoEstacionamento produto) {
        return new ProdutoEstacionamentoResponse(
                produto.getId(), produto.getOrganizacaoId(), produto.getEventoId(),
                produto.getAlocacaoId(), produto.getNome(), produto.getCategoria(),
                produto.getDireito(), produto.getInicioAcesso(), produto.getFimAcesso(),
                produto.getQuota(), produto.getStatus(), List.copyOf(produto.getBeneficios()),
                List.copyOf(produto.getRestricoes()), produto.getVersion(),
                produto.getCreatedAt(), produto.getUpdatedAt());
    }
}
