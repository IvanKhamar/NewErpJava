package ua.newerp.infrastructure.delivery;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.delivery.InternationalOrderInfo;
import ua.newerp.application.delivery.OpenShipment;
import ua.newerp.application.delivery.OrderShippingInfo;
import ua.newerp.application.delivery.ReminderDto;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.application.delivery.ShipmentRepository;
import ua.newerp.application.delivery.StoredShipment;
import ua.newerp.domain.delivery.ShipmentDocument;

@Repository
@Transactional(readOnly = true)
class JpaShipmentRepository implements ShipmentRepository {

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaShipmentRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShipmentDto> getForOrder(int orderId) {
        return latest(orderId).map(JpaShipmentRepository::toDto);
    }

    @Override
    public List<OpenShipment> listOpenForTracking() {
        List<ShipmentDocument> documents = em.createQuery("""
                SELECT d FROM ShipmentDocument d
                WHERE d.number <> '' AND d.statusCode NOT IN :finalCodes AND d.carrier <> :international
                """, ShipmentDocument.class)
                .setParameter("finalCodes", ShipmentDocument.FINAL_STATUS_CODES)
                // Waybills abroad are not known to the domestic API: asking about them would only bring errors.
                .setParameter("international", ShipmentDocument.INTERNATIONAL)
                .getResultList();
        if (documents.isEmpty()) {
            return List.of();
        }
        // dd_order has no JPA mapping, so the phones come with plain SQL. A waybill without an order is skipped (inner join in .NET).
        Map<Integer, String> phones = new HashMap<>();
        jdbc.sql("SELECT order_id, telephone FROM dd_order WHERE order_id IN (:ids)")
                .param("ids", documents.stream().map(ShipmentDocument::getOrderId).distinct().toList())
                .query(rs -> {
                    phones.put(rs.getInt("order_id"), rs.getString("telephone"));
                });
        return documents.stream()
                .filter(d -> phones.containsKey(d.getOrderId()))
                .map(d -> new OpenShipment(d, phones.get(d.getOrderId())))
                .toList();
    }

    @Override
    @Transactional
    public void saveTracking(Collection<ShipmentDocument> documents) {
        // The documents were loaded in another (already closed) transaction: merge copies their state into this one.
        documents.forEach(em::merge);
    }

    @Override
    public List<ReminderDto> listReminders(LocalDate keepingFrom, LocalDate keepingTo) {
        return jdbc.sql("""
                SELECT o.order_id, o.firstname, o.lastname, o.telephone, d.number, d.status, d.date_payed_keeping,
                       u.firstname AS manager_first, u.lastname AS manager_last
                FROM dd_novaposhta_document d
                JOIN dd_order o ON o.order_id = d.order_id
                LEFT JOIN erp_order_manager m ON m.order_id = o.order_id
                LEFT JOIN dd_user u ON u.user_id = m.user_id
                WHERE d.status_code IN (:waiting)
                  AND d.date_payed_keeping BETWEEN :from AND :to
                  AND o.date_remind IS NULL
                ORDER BY d.date_payed_keeping
                """)
                .param("waiting", ShipmentDocument.WAITING_AT_BRANCH_CODES)
                .param("from", keepingFrom)
                .param("to", keepingTo)
                .query((rs, n) -> new ReminderDto(
                        rs.getInt("order_id"),
                        (rs.getString("firstname") + " " + rs.getString("lastname")).strip(),
                        rs.getString("telephone"),
                        rs.getString("number"),
                        rs.getString("status"),
                        rs.getObject("date_payed_keeping", Date.class).toLocalDate(),
                        rs.getString("manager_first") == null ? null
                                : (rs.getString("manager_first") + " " + rs.getString("manager_last")).strip()))
                .list();
    }

    @Override
    @Transactional
    public boolean markReminded(int orderId, LocalDateTime remindedAt) {
        return jdbc.sql("UPDATE dd_order SET date_remind = :at WHERE order_id = :id")
                .param("at", remindedAt)
                .param("id", orderId)
                .update() > 0;
    }

    @Override
    public Optional<StoredShipment> getStored(int orderId) {
        return latest(orderId).map(document -> new StoredShipment(document,
                jdbc.sql("SELECT sender_id FROM erp_shipment_sender WHERE shipment_id = :id")
                        .param("id", document.getId())
                        .query(String.class)
                        .optional()
                        .orElse(null)));
    }

    @Override
    public Optional<InternationalOrderInfo> getInternationalOrderInfo(int orderId) {
        List<InternationalOrderInfo.Line> lines = jdbc.sql("""
                SELECT name, quantity, total FROM dd_order_product WHERE order_id = :id ORDER BY order_product_id
                """)
                .param("id", orderId)
                .query((rs, n) -> new InternationalOrderInfo.Line(rs.getString("name"), rs.getInt("quantity"), rs.getBigDecimal("total")))
                .list();
        // As for the domestic form: the shipping address, or the buyer if it is empty.
        return jdbc.sql("""
                SELECT IF(shipping_firstname <> '', shipping_firstname, firstname) AS first_name,
                       IF(shipping_lastname <> '', shipping_lastname, lastname) AS last_name,
                       telephone, email, shipping_country, shipping_zone, shipping_city, shipping_postcode,
                       TRIM(CONCAT(shipping_address_1, ' ', shipping_address_2)) AS address, total, currency_code
                FROM dd_order WHERE order_id = :id
                """)
                .param("id", orderId)
                .query((rs, n) -> new InternationalOrderInfo(rs.getString("first_name"), rs.getString("last_name"), rs.getString("telephone"),
                        rs.getString("email"), rs.getString("shipping_country"), rs.getString("shipping_zone"), rs.getString("shipping_city"),
                        rs.getString("shipping_postcode"), rs.getString("address"), rs.getBigDecimal("total"), rs.getString("currency_code"),
                        lines))
                .optional();
    }

    @Override
    public Optional<OrderShippingInfo> getOrderShippingInfo(int orderId) {
        // The recipient comes from the shipping address, or the buyer if it is empty. The old site writes the branch to address_2.
        return jdbc.sql("""
                SELECT IF(shipping_firstname <> '', shipping_firstname, firstname) AS first_name,
                       IF(shipping_lastname <> '', shipping_lastname, lastname) AS last_name,
                       telephone, shipping_city,
                       IF(shipping_address_2 <> '', shipping_address_2, shipping_address_1) AS warehouse,
                       total, payment_code, prepayment
                FROM dd_order WHERE order_id = :id
                """)
                .param("id", orderId)
                .query((rs, n) -> new OrderShippingInfo(rs.getString("first_name"), rs.getString("last_name"),
                        rs.getString("telephone"), rs.getString("shipping_city"), rs.getString("warehouse"),
                        rs.getBigDecimal("total"), rs.getString("payment_code"), rs.getBigDecimal("prepayment")))
                .optional();
    }

    @Override
    @Transactional
    public void add(ShipmentDocument document, String senderId, int userId, LocalDateTime createdAt) {
        em.persist(document); // IDENTITY: the INSERT runs right away and the id is known
        jdbc.sql("""
                INSERT INTO erp_shipment_sender (shipment_id, sender_id, created_by_user_id, created_at)
                VALUES (:id, :sender, :user, :at)
                """)
                .param("id", document.getId())
                .param("sender", senderId)
                .param("user", userId)
                .param("at", createdAt)
                .update();
    }

    @Override
    @Transactional
    public void delete(int documentId) {
        jdbc.sql("DELETE FROM erp_shipment_sender WHERE shipment_id = :id").param("id", documentId).update();
        jdbc.sql("DELETE FROM dd_novaposhta_document WHERE id = :id").param("id", documentId).update();
    }

    /** One waybill per order; if there are several (re-created), the latest one is shown, as on the old site. */
    private Optional<ShipmentDocument> latest(int orderId) {
        return em.createQuery("SELECT d FROM ShipmentDocument d WHERE d.orderId = :id ORDER BY d.id DESC", ShipmentDocument.class)
                .setParameter("id", orderId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    private static ShipmentDto toDto(ShipmentDocument d) {
        return new ShipmentDto(d.getId(), d.getOrderId(), d.getCarrier(), d.getNumber(), d.getCost(), d.getStatus(), d.getStatusCode(),
                d.getTone().ordinal(), d.isFinal(), d.getRecipientAddress(), d.isRedelivery(), d.getDateCreated(),
                d.getDateScheduled(), d.getDatePayedKeeping());
    }
}
