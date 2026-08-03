package com.kaneko.estacionamento.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoDataSeedService seedService;
    private final String adminLogin;

    public DemoDataInitializer(
            DemoDataSeedService seedService,
            @Value("${app.bootstrap-admin.login:admin}") String adminLogin) {
        this.seedService = seedService;
        this.adminLogin = adminLogin;
    }

    @Override
    public void run(String... args) {
        DemoDataSeedResult result = seedService.seed(adminLogin);
        if (!result.created()) {
            log.info("Cenario demo ja existe; seed nao executado novamente.");
            return;
        }
        log.info(
                "Cenario demo criado: organizationId={}, membershipId={}, venueId={}, "
                        + "parkingFacilityId={}, sectorId={}, eventId={}, "
                        + "parkingAllocationId={}, parkingProductId={}, priceTierId={}.",
                result.organizationId(), result.membershipId(), result.venueId(),
                result.parkingFacilityId(), result.sectorId(), result.eventId(),
                result.parkingAllocationId(), result.parkingProductId(), result.priceTierId());
    }
}
