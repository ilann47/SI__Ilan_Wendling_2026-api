package com.kaneko.estacionamento.rbac;

public class VersaoAtribuicaoDivergenteException extends RuntimeException {
    public VersaoAtribuicaoDivergenteException() { super("A versao da atribuicao divergiu."); }
}
