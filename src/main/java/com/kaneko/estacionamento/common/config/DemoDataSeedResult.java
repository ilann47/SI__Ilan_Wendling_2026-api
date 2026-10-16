package com.kaneko.estacionamento.common.config;

public record DemoDataSeedResult(
        boolean created,
        Long organizationId,
        Long membershipId,
        Long venueId,
        Long parkingFacilityId,
        Long sectorId,
        Long eventId,
        Long parkingAllocationId,
        Long parkingProductId,
        Long priceTierId) {

    static DemoDataSeedResult alreadyPresent() {
        return new DemoDataSeedResult(
                false, null, null, null, null, null, null, null, null, null);
    }
}
