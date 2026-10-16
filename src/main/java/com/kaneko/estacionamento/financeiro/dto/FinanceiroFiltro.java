package com.kaneko.estacionamento.financeiro.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Mesmo filtro para página e totais; organização é sempre resolvida pelo JWT. */
public record FinanceiroFiltro(
        @Size(max = 150) String search,
        @Positive Long fornecedorId, @Positive Long clienteId,
        @Size(max = 20) String situacao,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataVencimentoDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataVencimentoAte,
        Origem origem,
        @Positive Long notaEntradaId, @Positive Long notaSaidaId,
        @Positive Long notaServicoId, @Positive Long vendaAdministrativaId) {
    public enum Origem { MANUAL, NOTA_ENTRADA, NOTA_SAIDA, NOTA_SERVICO, VENDA, MENSALIDADE }
    @AssertTrue(message = "O início do vencimento não pode ser posterior ao fim.")
    public boolean isPeriodoValido() {
        return dataVencimentoDe == null || dataVencimentoAte == null
                || !dataVencimentoDe.isAfter(dataVencimentoAte);
    }
    public static FinanceiroFiltro vazio() {
        return new FinanceiroFiltro(null, null, null, null, null, null, null, null, null, null, null);
    }
}
