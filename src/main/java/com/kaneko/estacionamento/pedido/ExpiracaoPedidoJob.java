package com.kaneko.estacionamento.pedido;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.orders.expiration-enabled", havingValue = "true", matchIfMissing = true)
public class ExpiracaoPedidoJob {
    private final PedidoService service;

    public ExpiracaoPedidoJob(PedidoService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiration-sweep-ms:30000}")
    public void expirarVencidos() {
        service.expirarVencidos(100);
    }
}
