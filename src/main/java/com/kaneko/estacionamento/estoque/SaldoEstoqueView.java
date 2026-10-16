package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;

public interface SaldoEstoqueView {
    Long getId();
    Long getProdutoId();
    String getProduto();
    Long getLocalEstoqueId();
    String getLocalEstoque();
    BigDecimal getQuantidade();
    Long getVersion();
}
