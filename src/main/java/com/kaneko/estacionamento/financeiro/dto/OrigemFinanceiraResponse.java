package com.kaneko.estacionamento.financeiro.dto;

import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaReceber;

public record OrigemFinanceiraResponse(String tipo, Long id, String numero, String url) {
    public static OrigemFinanceiraResponse from(ContaPagar conta) {
        var nota = conta.getNotaEntrada();
        return nota == null ? manual() : new OrigemFinanceiraResponse("NOTA_ENTRADA", nota.getId(), nota.getNumero(),
                "/app/notas-entrada/" + nota.getId());
    }
    public static OrigemFinanceiraResponse from(ContaReceber conta) {
        if (conta.getVendaAdministrativa() != null) {
            Long id = conta.getVendaAdministrativa().getId();
            return new OrigemFinanceiraResponse("VENDA", id, String.valueOf(id), "/app/vendas-administrativas?detail=" + id);
        }
        if (conta.getNotaSaida() != null) {
            var nota = conta.getNotaSaida();
            return new OrigemFinanceiraResponse("NOTA_SAIDA", nota.getId(), nota.getNumero(), "/app/notas-saida?detail=" + nota.getId());
        }
        if (conta.getNotaServico() != null) {
            var nota = conta.getNotaServico();
            return new OrigemFinanceiraResponse("NOTA_SERVICO", nota.getId(), nota.getNumero(), "/app/notas-servico?detail=" + nota.getId());
        }
        if (conta.getMensalista() != null) {
            Long id = conta.getMensalista().getId();
            return new OrigemFinanceiraResponse("MENSALIDADE", id, String.valueOf(id), "/app/mensalistas?detail=" + id);
        }
        return manual();
    }
    private static OrigemFinanceiraResponse manual() { return new OrigemFinanceiraResponse("MANUAL", null, null, null); }
}
