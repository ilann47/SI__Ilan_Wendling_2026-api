package com.kaneko.estacionamento.fiscal.importacao;
import java.math.BigDecimal;
import java.util.List;
public record ImportacaoNotaResponse(Long id, Long version, StatusImportacaoNota status,
        DocumentoXmlEntrada documento, Long fornecedorId, Long localEstoqueId,
        Long condicaoPagamentoId, Long notaEntradaId,
        List<Item> itens, List<String> divergencias) {
    public record Item(int sequencia, Long produtoId, String produtoNome, String unidadeDestino,
            BigDecimal fatorConversao, BigDecimal quantidade, BigDecimal valorUnitario,
            BigDecimal quantidadeConferida, String motivoDivergencia) { }
}
