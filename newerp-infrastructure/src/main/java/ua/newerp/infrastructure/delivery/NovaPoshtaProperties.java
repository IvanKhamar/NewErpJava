package ua.newerp.infrastructure.delivery;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "nova-poshta" section. API keys only in application-local.yml or environment variables,
 * never in application.yml. There can be several senders (for example, different sole proprietors), each with its own key.
 *
 * @param apiKey        key for tracking and the directory; if not set, the default sender's key is used
 * @param fakeDocuments only for local development: without a key the stub "creates" test waybills with a number
 *                      starting with 00, so the whole path can be walked in the UI. Ignored when a key is set.
 */
@ConfigurationProperties("nova-poshta")
public record NovaPoshtaProperties(
        String apiKey,
        String baseUrl,
        String printBaseUrl,
        String cargoDescription,
        boolean fakeDocuments,
        List<Sender> senders,
        List<DropoffPoint> dropoffPoints) {

    public NovaPoshtaProperties {
        baseUrl = baseUrl == null ? "https://api.novaposhta.ua/v2.0/json/" : baseUrl;
        printBaseUrl = printBaseUrl == null ? "https://my.novaposhta.ua/" : printBaseUrl;
        // Cargo description in the waybill. The old site writes "Vidro".
        cargoDescription = cargoDescription == null ? "Vidro" : cargoDescription;
        senders = senders == null ? List.of() : List.copyOf(senders);
        dropoffPoints = dropoffPoints == null ? List.of() : List.copyOf(dropoffPoints);
    }

    /**
     * A sender: counterparty and contact person in the Nova Poshta account (their Refs come from the API or the account).
     *
     * @param id    short id for the database (erp_shipment_sender.sender_id), for example "fop-ivanenko". Do not change after the first waybill.
     * @param phone sender's phone, 380XXXXXXXXX
     */
    public record Sender(String id, String name, String apiKey, String counterpartyRef, String contactRef, String phone,
            boolean isDefault) {
    }

    /** Where parcels are taken to: a branch (Refs of the city and the branch). */
    public record DropoffPoint(String id, String name, String cityRef, String addressRef, boolean isDefault) {
    }

    /** Key for tracking and the directory: its own, or the default sender's. */
    public String trackingKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        return senders.stream().filter(Sender::isDefault).findFirst()
                .or(() -> senders.stream().findFirst())
                .map(Sender::apiKey)
                .orElse(null);
    }
}
