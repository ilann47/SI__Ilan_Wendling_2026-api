package com.kaneko.estacionamento.inventario.produto;

import java.time.OffsetDateTime;

public interface ProdutoDisponibilidadeView {
    Long getProdutoId();
    Long getAlocacaoId();
    String getNome();
    CategoriaProduto getCategoria();
    DireitoProduto getDireito();
    OffsetDateTime getInicioAcesso();
    OffsetDateTime getFimAcesso();
    Integer getQuota();
}
