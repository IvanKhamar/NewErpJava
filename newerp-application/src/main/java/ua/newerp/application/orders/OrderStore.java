package ua.newerp.application.orders;

import java.time.LocalDateTime;
import java.util.Optional;

import ua.newerp.domain.orders.OrderStatusChange;

/** What the order change use cases need from the database. */
public interface OrderStore {

    /** @return the current status, or empty when there is no such order */
    Optional<Integer> getStatusId(int orderId);

    boolean statusExists(int statusId);

    boolean isActiveUser(int userId);

    /** Updates the order's status and writes a row to the status log. */
    void saveStatusChange(OrderStatusChange change);

    void saveManager(int orderId, int managerId, int assignedByUserId, LocalDateTime assignedAt);
}
