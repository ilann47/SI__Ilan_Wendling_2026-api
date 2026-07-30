package com.kaneko.estacionamento.inventario.produto.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record CriarProdutoEstacionamentoRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull CategoriaProduto category,
        @NotNull Long parkingAllocationId,
        @NotNull OffsetDateTime accessStartsAt,
        @NotNull OffsetDateTime accessEndsAt,
        @NotNull DireitoProduto right,
        @NotNull @Min(1) Integer quota,
        @Size(max = 50) List<String> benefits,
        @Size(max = 50) List<String> restrictions) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
