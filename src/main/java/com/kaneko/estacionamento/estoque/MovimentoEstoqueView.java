package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;
import java.time.Instant;

public interface MovimentoEstoqueView {
    Long getId();
    Long getProdutoId();
    String getProduto();
    Long getLocalEstoqueId();
    String getLocalEstoque();
    Long getSaldoEstoqueId();
    String getTipo();
    BigDecimal getDelta();
    BigDecimal getSaldoAnterior();
    BigDecimal getSaldoPosterior();
    BigDecimal getCustoUnitario();
    String getOrigemTipo();
    String getOrigemChave();
    Long getCompensadoId();
    Long getAtorId();
    String getMotivo();
    Instant getOcorridoEm();
    Instant getCreatedAt();
}
