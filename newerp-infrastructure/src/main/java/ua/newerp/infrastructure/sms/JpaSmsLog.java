package ua.newerp.infrastructure.sms;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import ua.newerp.application.sms.SmsDto;
import ua.newerp.application.sms.SmsLog;
import ua.newerp.domain.sms.SmsMessage;

/** The old site's SMS log dd_sms_log (structure reconstructed, see db/reconstructed.sql). */
@Repository
class JpaSmsLog implements SmsLog {

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaSmsLog(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> getOrderPhone(int orderId) {
        return jdbc.sql("SELECT telephone FROM dd_order WHERE order_id = :id").param("id", orderId).query(String.class).optional();
    }

    @Override
    public List<SmsDto> listForOrder(int orderId) {
        return jdbc.sql("""
                SELECT message_id, order_id, phone, text, response, date
                FROM dd_sms_log WHERE order_id = :id ORDER BY date DESC, message_id DESC
                """)
                .param("id", orderId)
                .query((rs, n) -> new SmsDto(rs.getInt("message_id"), rs.getInt("order_id"), rs.getString("phone"),
                        rs.getString("text"), rs.getString("response"), rs.getTimestamp("date").toLocalDateTime()))
                .list();
    }

    @Override
    public Map<String, Integer> findOrderIdsByTtn(Collection<String> numbers) {
        Map<String, Integer> owners = new LinkedHashMap<>();
        if (numbers.isEmpty()) {
            return owners;
        }
        // As on the old site: if a number repeats, the latest row wins (putIfAbsent keeps the first, rows come newest first)
        jdbc.sql("SELECT number, order_id FROM dd_novaposhta_document WHERE number IN (:numbers) ORDER BY id DESC")
                .param("numbers", numbers)
                .query(rs -> {
                    owners.putIfAbsent(rs.getString("number"), rs.getInt("order_id"));
                });
        return owners;
    }

    @Override
    public void add(SmsMessage message) {
        em.persist(message);
    }
}
