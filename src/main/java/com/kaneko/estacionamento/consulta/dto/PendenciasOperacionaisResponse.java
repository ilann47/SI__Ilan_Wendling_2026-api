package com.kaneko.estacionamento.consulta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PendenciasOperacionaisResponse(LocalDate referencia, long total, int limitePorGrupo,
                                              List<Grupo> grupos) {
    public record Grupo(String tipo, long total, List<Item> itens) {}
    public record Item(Long id, String titulo, String contraparte, String caminho, LocalDate data, BigDecimal valor) {}
}
