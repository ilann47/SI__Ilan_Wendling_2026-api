package com.kaneko.estacionamento.inventario.reserva;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.inventory.expiration-enabled", havingValue = "true", matchIfMissing = true)
public class ExpiracaoReservaInventarioJob {
    private final ReservaInventarioService service;

    public ExpiracaoReservaInventarioJob(ReservaInventarioService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.inventory.expiration-sweep-ms:30000}")
    public void expirarVencidas() {
        service.expirarVencidas(100);
    }
}
