package com.kaneko.estacionamento.consulta.dto;

import java.util.List;

public record BuscaGlobalResponse(String termo, int total, List<GrupoBuscaGlobal> grupos) {
}
