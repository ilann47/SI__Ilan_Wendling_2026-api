package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;

public interface PosicaoEstoqueView {
    Long getProdutoId();
    String getProduto();
    BigDecimal getQuantidade();
    BigDecimal getQuantidadeMinima();
    Boolean getAbaixoMinimo();
}
