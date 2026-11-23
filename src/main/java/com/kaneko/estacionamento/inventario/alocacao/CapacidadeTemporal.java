package com.kaneko.estacionamento.inventario.alocacao;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Saldo de vagas em intervalos semiabertos [início, fim). */
public final class CapacidadeTemporal {
    private CapacidadeTemporal() {}

    public record Saldo(int operationalCapacity, int availableCapacity, List<Segmento> segments) {}

    public record Segmento(Instant startsAt, Instant endsAt, int availableCapacity) {}

    public static Saldo calcular(int capacidade, OffsetDateTime inicio, OffsetDateTime fim,
                                List<AlocacaoPatioEvento> alocacoes, boolean incluirHorarios) {
        var inicioJanela = inicio.toInstant();
        var fimJanela = fim.toInstant();
        var variacoes = new TreeMap<Instant, Long>();
        variacoes.put(inicioJanela, 0L);
        variacoes.put(fimJanela, 0L);
        for (var alocacao : alocacoes) {
            var entrada = alocacao.getInicio().toInstant();
            var saida = alocacao.getFim().toInstant();
            if (!entrada.isBefore(fimJanela) || !saida.isAfter(inicioJanela)) continue;
            long vagas = alocacao.getCapacidadeOperacional();
            variacoes.merge(entrada.isBefore(inicioJanela) ? inicioJanela : entrada, vagas, Long::sum);
            variacoes.merge(saida.isAfter(fimJanela) ? fimJanela : saida, -vagas, Long::sum);
        }

        long comprometidas = 0;
        int saldoMinimo = capacidade;
        var faixas = new ArrayList<Segmento>();
        Instant anterior = inicioJanela;
        for (var variacao : variacoes.entrySet()) {
            Instant limite = variacao.getKey();
            if (anterior.isBefore(limite)) {
                int saldo = (int) Math.max(0, capacidade - comprometidas);
                saldoMinimo = Math.min(saldoMinimo, saldo);
                if (incluirHorarios) {
                    if (!faixas.isEmpty() && faixas.getLast().availableCapacity() == saldo) {
                        anterior = faixas.removeLast().startsAt();
                    }
                    faixas.add(new Segmento(anterior, limite, saldo));
                }
            }
            anterior = limite;
            comprometidas += variacao.getValue();
        }
        return new Saldo(capacidade, saldoMinimo, List.copyOf(faixas));
    }
}
