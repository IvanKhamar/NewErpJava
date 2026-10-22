package ua.newerp.infrastructure.orders;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ua.newerp.application.orders.OrderStore;
import ua.newerp.domain.orders.OrderStatusChange;

/** OpenCart order tables through plain SQL: there are no domain classes for them, only a few columns change. */
@Repository
class JdbcOrderStore implements OrderStore {

    private final JdbcClient jdbc;

    JdbcOrderStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Integer> getStatusId(int orderId) {
        return jdbc.sql("SELECT order_status_id FROM dd_order WHERE order_id = :id").param("id", orderId)
                .query(Integer.class).optional();
    }

    @Override
    public boolean statusExists(int statusId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_order_status WHERE order_status_id = :id").param("id", statusId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public boolean isActiveUser(int userId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_user WHERE user_id = :id AND status = 1").param("id", userId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public void saveStatusChange(OrderStatusChange change) {
        // Note: OpenCart tables use the MyISAM engine, which has no transactions.
        // So the update and the log row are not atomic: after a failure between them the log may miss a row.
        jdbc.sql("UPDATE dd_order SET order_status_id = :status, date_modified = :at WHERE order_id = :id")
                .param("status", change.newStatusId())
                .param("at", change.changedAt())
                .param("id", change.orderId())
                .update();
        jdbc.sql("""
                INSERT INTO dd_order_status_log (order_id, user_id, old_status_id, new_status_id, date_added)
                VALUES (:orderId, :userId, :oldStatus, :newStatus, :at)
                """)
                .param("orderId", change.orderId())
                .param("userId", change.userId())
                .param("oldStatus", change.oldStatusId())
                .param("newStatus", change.newStatusId())
                .param("at", change.changedAt())
                .update();
    }

    @Override
    public void saveManager(int orderId, int managerId, int assignedByUserId, LocalDateTime assignedAt) {
        // One row per order: insert, or replace the manager if the order already has one
        jdbc.sql("""
                INSERT INTO erp_order_manager (order_id, user_id, assigned_by_user_id, assigned_at)
                VALUES (:orderId, :userId, :by, :at)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), assigned_by_user_id = VALUES(assigned_by_user_id),
                                        assigned_at = VALUES(assigned_at)
                """)
                .param("orderId", orderId)
                .param("userId", managerId)
                .param("by", assignedByUserId)
                .param("at", assignedAt)
                .update();
    }
}
