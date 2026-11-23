package com.kaneko.estacionamento.consulta.dto;

public record ResultadoBuscaGlobal(
        String tipo,
        Long id,
        String titulo,
        String subtitulo,
        String status,
        String caminho) {
}
