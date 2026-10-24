package ua.newerp.infrastructure.delivery;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import ua.newerp.application.delivery.CreatedShipment;
import ua.newerp.application.delivery.HsCodeDto;
import ua.newerp.application.delivery.NovaPostInternationalClient;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.InternationalShipment;

/**
 * The stub used when there is no Nova Post International key: calls nobody. With fake-documents (development only) it
 * "creates" test waybills numbered TEST… and offers one test customs code, so the whole path can be walked in the UI.
 */
class StubNovaPostInternationalClient implements NovaPostInternationalClient {

    private final boolean fakeDocuments;

    StubNovaPostInternationalClient(boolean fakeDocuments) {
        this.fakeDocuments = fakeDocuments;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public boolean canCreateDocuments() {
        return fakeDocuments;
    }

    @Override
    public List<HsCodeDto> searchHsCodes(String keyword, String countryCode) {
        return fakeDocuments ? List.of(new HsCodeDto("6204491000", "Сукні з інших текстильних матеріалів (заглушка)", "Dresses")) : List.of();
    }

    @Override
    public CreatedShipment create(InternationalShipment shipment) {
        if (!fakeDocuments) {
            throw new DomainException("Ключ API Nova Post International не задано, тож міжнародну накладну створити не можна.");
        }
        String number = "TEST" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
        return new CreatedShipment(UUID.randomUUID().toString(), number, shipment.declaredCost());
    }

    @Override
    public void delete(String shipmentId) {
        // Nothing to delete anywhere: test waybills exist only here.
    }
}
