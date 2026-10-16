package com.kaneko.estacionamento.inventario.produto;

public class VersaoProdutoDivergenteException extends RuntimeException {
    public VersaoProdutoDivergenteException() {
        super("O produto foi alterado por outra operacao.");
    }
}
