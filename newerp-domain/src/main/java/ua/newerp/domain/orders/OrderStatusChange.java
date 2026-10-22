package ua.newerp.domain.orders;

import java.time.LocalDateTime;

import ua.newerp.domain.common.DomainException;

/**
 * A change of order status to be written to the log.
 * A record is an immutable data class with equals/hashCode/toString generated, like a C# record.
 */
public record OrderStatusChange(int orderId, int oldStatusId, int newStatusId, int userId, LocalDateTime changedAt) {

    public static OrderStatusChange create(int orderId, int currentStatusId, int newStatusId, int userId, LocalDateTime now) {
        if (newStatusId <= 0) {
            throw new DomainException("Статус має бути додатним числом.");
        }
        if (newStatusId == currentStatusId) {
            throw new DomainException("Замовлення вже має цей статус.");
        }
        if (userId <= 0) {
            throw new DomainException("Не вказано користувача, який змінює статус.");
        }
        return new OrderStatusChange(orderId, currentStatusId, newStatusId, userId, now);
    }
}
