package ua.newerp.infrastructure.delivery;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import ua.newerp.application.delivery.CreatedShipment;
import ua.newerp.application.delivery.DropoffPointOption;
import ua.newerp.application.delivery.NewShipment;
import ua.newerp.application.delivery.NovaPoshtaClient;
import ua.newerp.application.delivery.SenderOption;
import ua.newerp.application.delivery.ShipmentPrintKind;
import ua.newerp.application.delivery.TrackingRequest;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.NovaPoshtaWarehouse;
import ua.newerp.domain.delivery.TrackingInfo;

/**
 * The stub used when there is no key: calls nobody. With fake-documents (development only) it "creates" test waybills
 * with a number starting with 00 from a test sender; there is nothing to print for them.
 */
class StubNovaPoshtaClient implements NovaPoshtaClient {

    static final String TEST_SENDER_ID = "test";
    static final String TEST_DROPOFF_POINT_ID = "test";

    private final boolean fakeDocuments;

    StubNovaPoshtaClient(boolean fakeDocuments) {
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
    public List<SenderOption> senders() {
        return fakeDocuments ? List.of(new SenderOption(TEST_SENDER_ID, "Тестовий відправник (заглушка)", true)) : List.of();
    }

    @Override
    public List<DropoffPointOption> dropoffPoints() {
        return fakeDocuments ? List.of(new DropoffPointOption(TEST_DROPOFF_POINT_ID, "Тестове відділення (заглушка)", true)) : List.of();
    }

    @Override
    public List<TrackingInfo> track(List<TrackingRequest> documents) {
        return List.of();
    }

    @Override
    public CreatedShipment create(NewShipment shipment) {
        if (!fakeDocuments) {
            throw new DomainException("Ключ API Нової Пошти не задано, тож ТТН створити не можна.");
        }
        String number = "00" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        return new CreatedShipment(UUID.randomUUID().toString(), number, BigDecimal.ZERO);
    }

    @Override
    public void delete(String senderId, String documentRef) {
        // nothing to delete: the waybill exists only in our database
    }

    @Override
    public byte[] print(String senderId, String documentRef, ShipmentPrintKind kind) {
        throw new DomainException("Друк недоступний: ключ API Нової Пошти не задано.");
    }

    @Override
    public List<NovaPoshtaWarehouse> fetchWarehouses() {
        return List.of();
    }
}
