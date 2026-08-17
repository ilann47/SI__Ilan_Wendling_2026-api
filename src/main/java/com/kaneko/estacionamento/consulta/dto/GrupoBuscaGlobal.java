package com.kaneko.estacionamento.consulta.dto;

import java.util.List;

public record GrupoBuscaGlobal(String grupo, List<ResultadoBuscaGlobal> resultados) {
}
