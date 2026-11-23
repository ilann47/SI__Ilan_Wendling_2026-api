package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;

public interface EstoqueMinimoTenantView {
    Long getProdutoId();
    String getProduto();
    String getMarca();
    String getCategoria();
    BigDecimal getQuantidade();
    BigDecimal getQuantidadeMinima();
}
