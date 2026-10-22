package ua.newerp.application.delivery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import ua.newerp.domain.delivery.ShipmentDocument;

public interface ShipmentRepository {

    Optional<ShipmentDto> getForOrder(int orderId);

    /** Waybills with a number that can still change (not received, not returned). */
    List<OpenShipment> listOpenForTracking();

    /** Saves tracking changes of waybills loaded by {@link #listOpenForTracking}. */
    void saveTracking(Collection<ShipmentDocument> documents);

    /** Parcels at the branch whose paid storage starts in this range, and the customer was not reminded yet. */
    List<ReminderDto> listReminders(LocalDate keepingFrom, LocalDate keepingTo);

    /** @return false if there is no such order */
    boolean markReminded(int orderId, LocalDateTime remindedAt);

    /** The latest waybill of the order, with its sender. */
    Optional<StoredShipment> getStored(int orderId);

    Optional<OrderShippingInfo> getOrderShippingInfo(int orderId);

    /** Saves a new waybill and the sender it was created from. */
    void add(ShipmentDocument document, String senderId, int userId, LocalDateTime createdAt);

    /** Deletes a waybill and its sender row. */
    void delete(int documentId);
}
