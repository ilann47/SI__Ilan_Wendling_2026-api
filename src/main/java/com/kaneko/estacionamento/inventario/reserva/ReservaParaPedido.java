package com.kaneko.estacionamento.inventario.reserva;

import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Snapshot comercial que o modulo de inventario oferece ao modulo de pedidos. */
public record ReservaParaPedido(
        Long reservaId,
        Long eventoId,
        Long produtoId,
        Long alocacaoId,
        Long patioId,
        Long lotePrecoId,
        String nomeProduto,
        CategoriaProduto categoria,
        DireitoProduto direito,
        OffsetDateTime inicioAcesso,
        OffsetDateTime fimAcesso,
        Integer quantidade,
        String canal,
        BigDecimal precoUnitario,
        String moeda,
        OffsetDateTime expiraEm) {}
