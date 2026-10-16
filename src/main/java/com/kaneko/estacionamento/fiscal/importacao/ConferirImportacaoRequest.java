package com.kaneko.estacionamento.fiscal.importacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
public record ConferirImportacaoRequest(@NotEmpty @Size(max=990) @Valid List<Item> itens) {
    public record Item(@Min(1) int sequencia,
            @NotNull @PositiveOrZero @Digits(integer=12,fraction=3) BigDecimal quantidadeConferida) { }
}
