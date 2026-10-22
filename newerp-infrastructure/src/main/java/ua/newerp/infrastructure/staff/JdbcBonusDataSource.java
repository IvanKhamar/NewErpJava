package ua.newerp.infrastructure.staff;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ua.newerp.application.staff.BonusDataSource;
import ua.newerp.domain.staff.BonusOrder;

@Repository
class JdbcBonusDataSource implements BonusDataSource {

    private final JdbcClient jdbc;

    JdbcBonusDataSource(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BonusOrder> getManagerOrders(LocalDateTime start, LocalDateTime endExclusive) {
        return jdbc.sql("""
                SELECT o.order_id, m.user_id, o.order_status_id, o.total
                FROM dd_order o
                JOIN erp_order_manager m ON m.order_id = o.order_id
                WHERE o.date_added >= :start AND o.date_added < :end
                """)
                .param("start", start)
                .param("end", endExclusive)
                .query((rs, n) -> new BonusOrder(rs.getInt("order_id"), rs.getInt("user_id"), rs.getInt("order_status_id"),
                        rs.getBigDecimal("total")))
                .list();
    }

    @Override
    public Map<Integer, String> getUserNames(Collection<Integer> userIds) {
        Map<Integer, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            jdbc.sql("SELECT user_id, firstname, lastname FROM dd_user WHERE user_id IN (:ids)")
                    .param("ids", userIds)
                    .query(rs -> {
                        names.put(rs.getInt("user_id"), rs.getString("firstname") + " " + rs.getString("lastname"));
                    });
        }
        return names;
    }
}
