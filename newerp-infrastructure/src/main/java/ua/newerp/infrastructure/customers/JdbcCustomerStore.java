package ua.newerp.infrastructure.customers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.application.customers.CustomerAddressDto;
import ua.newerp.application.customers.CustomerFilter;
import ua.newerp.application.customers.CustomerGroupDto;
import ua.newerp.application.customers.CustomerListItem;
import ua.newerp.application.customers.CustomerNoteDto;
import ua.newerp.application.customers.CustomerOrderItem;
import ua.newerp.application.customers.CustomerProfile;
import ua.newerp.application.customers.CustomerStore;
import ua.newerp.domain.orders.CustomerRisk;
import ua.newerp.infrastructure.ErpProperties;

/** OpenCart's customer tables. The customer's password, salt, token and cart are never read: every query picks only the columns it needs. */
@Repository
class JdbcCustomerStore implements CustomerStore {

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcCustomerStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public PagedResult<CustomerListItem> list(CustomerFilter filter, Paging paging) {
        var where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        params.put("languageId", languageId);
        if (filter.search() != null) {
            params.put("term", filter.search());
            if (filter.search().matches("-?\\d+")) {
                where.append(" AND (c.customer_id = :id OR LOCATE(:term, c.telephone) > 0)");
                params.put("id", Long.parseLong(filter.search()));
            } else {
                where.append(" AND (LOCATE(:term, CONCAT(c.firstname, ' ', c.lastname)) > 0 OR LOCATE(:term, c.email) > 0"
                        + " OR LOCATE(:term, c.telephone) > 0)");
            }
        }
        if (filter.groupId() != null) {
            where.append(" AND c.customer_group_id = :groupId");
            params.put("groupId", filter.groupId());
        }
        if (filter.status() != null) {
            where.append(" AND c.status = :status");
            params.put("status", filter.status());
        }

        int total = jdbc.sql("SELECT COUNT(*) FROM dd_customer c" + where).params(params).query(Integer.class).single();
        params.put("limit", paging.pageSize());
        params.put("offset", paging.offset());
        List<CustomerListItem> items = jdbc.sql("""
                SELECT c.customer_id, CONCAT(c.firstname, ' ', c.lastname) AS name, c.email, c.telephone, g.name AS group_name, c.status,
                       c.date_added,
                       (SELECT COUNT(*) FROM dd_order o WHERE o.customer_id = c.customer_id AND o.order_status_id > 0) AS order_count
                FROM dd_customer c
                LEFT JOIN dd_customer_group_description g ON g.customer_group_id = c.customer_group_id AND g.language_id = :languageId
                """ + where + " ORDER BY c.date_added DESC, c.customer_id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new CustomerListItem(rs.getInt("customer_id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("telephone"), rs.getString("group_name"), rs.getBoolean("status"),
                        rs.getObject("date_added", LocalDateTime.class), rs.getInt("order_count")))
                .list();
        return PagedResult.of(items, paging.page(), paging.pageSize(), total);
    }

    @Override
    public List<CustomerGroupDto> groups() {
        return jdbc.sql("SELECT customer_group_id, name FROM dd_customer_group_description WHERE language_id = :languageId ORDER BY name")
                .param("languageId", languageId)
                .query((rs, n) -> new CustomerGroupDto(rs.getInt("customer_group_id"), rs.getString("name")))
                .list();
    }

    @Override
    public Optional<CustomerProfile> get(int customerId) {
        record Customer(int id, String firstname, String lastname, String email, String telephone, String groupName, boolean status,
                boolean newsletter, int addressId, LocalDateTime dateAdded) {
        }
        Optional<Customer> customer = jdbc.sql("""
                SELECT c.customer_id, c.firstname, c.lastname, c.email, c.telephone, g.name AS group_name, c.status, c.newsletter,
                       c.address_id, c.date_added
                FROM dd_customer c
                LEFT JOIN dd_customer_group_description g ON g.customer_group_id = c.customer_group_id AND g.language_id = :languageId
                WHERE c.customer_id = :id
                """)
                .param("languageId", languageId).param("id", customerId)
                .query((rs, n) -> new Customer(rs.getInt("customer_id"), rs.getString("firstname"), rs.getString("lastname"),
                        rs.getString("email"), rs.getString("telephone"), rs.getString("group_name"), rs.getBoolean("status"),
                        rs.getBoolean("newsletter"), rs.getInt("address_id"), rs.getObject("date_added", LocalDateTime.class)))
                .optional();
        if (customer.isEmpty()) {
            return Optional.empty();
        }
        Customer c = customer.get();

        List<CustomerAddressDto> addresses = jdbc.sql("""
                SELECT address_id, CONCAT(firstname, ' ', lastname) AS name, city, address_1, address_2
                FROM dd_address WHERE customer_id = :id ORDER BY address_id
                """)
                .param("id", customerId)
                .query((rs, n) -> new CustomerAddressDto(rs.getInt("address_id"), rs.getString("name"), rs.getString("city"),
                        rs.getString("address_1"), rs.getString("address_2"), rs.getInt("address_id") == c.addressId()))
                .list();
        List<CustomerNoteDto> notes = jdbc.sql("""
                SELECT date_added, comment FROM dd_customer_history WHERE customer_id = :id
                ORDER BY date_added DESC, customer_history_id DESC
                """)
                .param("id", customerId)
                .query((rs, n) -> new CustomerNoteDto(rs.getObject("date_added", LocalDateTime.class), rs.getString("comment")))
                .list();

        return Optional.of(new CustomerProfile(c.id(), c.firstname(), c.lastname(), c.email(), c.telephone(), c.groupName(), c.status(),
                c.newsletter(), c.dateAdded(), addresses, notes));
    }

    @Override
    public List<CustomerOrderItem> orders(int customerId, String phoneKey) {
        record Row(CustomerOrderItem item, int customerId, String telephone) {
        }
        // Phones in orders are written any way, so the database picks by digits and the exact check is done here.
        return jdbc.sql("""
                SELECT o.order_id, o.date_added, o.order_status_id, s.name AS status_name, o.total, o.currency_code, o.customer_id, o.telephone
                FROM dd_order o
                LEFT JOIN dd_order_status s ON s.order_status_id = o.order_status_id AND s.language_id = :languageId
                WHERE o.order_status_id > 0
                  AND (o.customer_id = :customerId
                       OR (:phoneKey IS NOT NULL
                           AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(o.telephone, '+', ''), '-', ''), ' ', ''), '(', ''), ')', '')
                               LIKE CONCAT('%', :phoneKey)))
                ORDER BY o.date_added DESC, o.order_id DESC
                """)
                .param("languageId", languageId).param("customerId", customerId).param("phoneKey", phoneKey)
                .query((rs, n) -> new Row(new CustomerOrderItem(rs.getInt("order_id"), rs.getObject("date_added", LocalDateTime.class),
                        rs.getInt("order_status_id"), rs.getString("status_name"), rs.getBigDecimal("total"), rs.getString("currency_code"),
                        rs.getInt("customer_id") != customerId), rs.getInt("customer_id"), rs.getString("telephone")))
                .list().stream()
                .filter(r -> r.customerId() == customerId || Objects.equals(CustomerRisk.phoneKey(r.telephone()), phoneKey))
                .map(Row::item)
                .toList();
    }

    @Override
    public boolean addNote(int customerId, String comment, LocalDateTime now) {
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM dd_customer WHERE customer_id = :id").param("id", customerId)
                .query(Integer.class).single() > 0;
        if (!exists) {
            return false;
        }
        jdbc.sql("INSERT INTO dd_customer_history (customer_id, comment, date_added) VALUES (:id, :comment, :now)")
                .param("id", customerId).param("comment", comment).param("now", now)
                .update();
        return true;
    }
}
