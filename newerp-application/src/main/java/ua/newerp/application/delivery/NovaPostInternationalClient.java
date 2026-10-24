package ua.newerp.application.delivery;

import java.util.List;

import ua.newerp.domain.delivery.InternationalShipment;

/** Nova Post International (api.novapost.com): waybills abroad. Without a key a stub stands in (see DeliveryConfig). */
public interface NovaPostInternationalClient {

    /** A real key is set. */
    boolean isConfigured();

    /** Waybills can be created: with a key, or with fake-documents in development. */
    boolean canCreateDocuments();

    /** Customs codes (HS) by a word, for the recipient's country. */
    List<HsCodeDto> searchHsCodes(String keyword, String countryCode);

    /** @return the shipment id (kept as the document's ref), its number and cost */
    CreatedShipment create(InternationalShipment shipment);

    void delete(String shipmentId);
}
