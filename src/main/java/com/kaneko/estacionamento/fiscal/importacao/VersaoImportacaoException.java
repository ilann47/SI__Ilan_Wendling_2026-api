package com.kaneko.estacionamento.fiscal.importacao;
public class VersaoImportacaoException extends RuntimeException {
    public VersaoImportacaoException() { super("A importação foi alterada. Recarregue antes de continuar."); }
}
