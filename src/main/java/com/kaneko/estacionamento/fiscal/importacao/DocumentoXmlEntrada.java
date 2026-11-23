package com.kaneko.estacionamento.fiscal.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DocumentoXmlEntrada(String chave, String numero, String serie, String modelo,
        String emitenteDocumento, String emitenteNome, String destinatarioDocumento,
        LocalDate emissao, BigDecimal frete, BigDecimal seguro, BigDecimal outrasDespesas,
        BigDecimal total, List<Item> itens) {
    public record Item(int sequencia, String codigo, String descricao, String unidade,
            BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorProdutos,
            BigDecimal desconto) { }
}
