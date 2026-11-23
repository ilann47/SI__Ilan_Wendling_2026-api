package com.kaneko.estacionamento.pedido.dto;

import com.kaneko.estacionamento.pedido.CompradorPedido;

public record CompradorPedidoResponse(
        String name,
        String email,
        String maskedDocument) {

    public static CompradorPedidoResponse from(CompradorPedido comprador) {
        String documento = comprador.getDocumento();
        String mascarado = documento == null ? null
                : "*".repeat(documento.length() - 4)
                        + documento.substring(documento.length() - 4);
        return new CompradorPedidoResponse(
                comprador.getNome(), comprador.getEmail(), mascarado);
    }
}
