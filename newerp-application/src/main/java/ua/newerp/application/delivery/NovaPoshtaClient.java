package ua.newerp.application.delivery;

import java.util.List;

import ua.newerp.domain.delivery.NovaPoshtaWarehouse;
import ua.newerp.domain.delivery.TrackingInfo;

/**
 * The Nova Poshta API. Without a key in the settings a stub is registered that calls nobody:
 * so local development can never reach the real service by accident.
 * Only the implementation knows the senders' API keys; outside there are only ids and names.
 */
public interface NovaPoshtaClient {

    /** Whether a key is set, that is, whether this is the real client. */
    boolean isConfigured();

    /** Whether waybills can be created: the real client, or the stub with test waybills (nova-poshta.fake-documents). */
    boolean canCreateDocuments();

    List<SenderOption> senders();

    List<DropoffPointOption> dropoffPoints();

    /** Waybill statuses (TrackingDocument.getStatusDocuments). Read only: creates and changes nothing. */
    List<TrackingInfo> track(List<TrackingRequest> documents);

    /** Creates the recipient and the express waybill (Counterparty.save + InternetDocument.save). */
    CreatedShipment create(NewShipment shipment);

    /** Deletes a waybill in Nova Poshta (InternetDocument.delete) with the key of the sender that created it. */
    void delete(String senderId, String documentRef);

    /** PDF of a waybill or markings. The API key stays on the server, the browser gets only the file. */
    byte[] print(String senderId, String documentRef, ShipmentPrintKind kind);

    /** The whole directory of branches and parcel lockers (Address.getWarehouses page by page). */
    List<NovaPoshtaWarehouse> fetchWarehouses();
}
