package ua.newerp.infrastructure.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.orders.CreateOrderRequest;
import ua.newerp.application.orders.CustomerPickDto;
import ua.newerp.application.orders.NewOrderLineRecord;
import ua.newerp.application.orders.NewOrderRecord;
import ua.newerp.application.orders.NewOrderStore;
import ua.newerp.application.orders.OrderCompositionDto;
import ua.newerp.application.orders.OrderCompositionLine;
import ua.newerp.application.orders.OrderMethodDto;
import ua.newerp.application.orders.OrderMethodsDto;
import ua.newerp.application.orders.ProductOptionValueDto;
import ua.newerp.application.orders.ProductPickDto;
import ua.newerp.domain.orders.CustomerOrder;
import ua.newerp.domain.orders.CustomerRisk;
import ua.newerp.domain.orders.OrderDraft;
import ua.newerp.infrastructure.ErpProperties;

/** New orders in the OpenCart tables through plain SQL (dd_order has some 70 columns; only inserts and a few updates are needed). */
@Repository
class JdbcNewOrderStore implements NewOrderStore {

    /** Ukraine's id in the standard OpenCart country list; used only while the database has no order yet. */
    private static final int UKRAINE_COUNTRY_ID = 220;

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcNewOrderStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public List<ProductPickDto> searchProducts(String term, int limit) {
        int id;
        try {
            id = Integer.parseInt(term);
        } catch (NumberFormatException e) {
            id = 0;
        }
        List<Integer> ids = jdbc.sql("""
                SELECT p.product_id FROM dd_product p
                JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                WHERE p.status = 1 AND (p.product_id = :id OR LOCATE(:term, d.name) > 0 OR LOCATE(:term, p.model) > 0)
                ORDER BY d.name
                LIMIT :limit
                """)
                .param("languageId", languageId).param("id", id).param("term", term).param("limit", limit)
                .query(Integer.class).list();
        return getProducts(ids);
    }

    @Override
    public List<ProductPickDto> getProducts(Collection<Integer> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        // Only the needed columns: date_available in the real database can be '0000-00-00', which a date cannot hold
        record Product(int id, String name, String model, BigDecimal price, int quantity) {
        }
        List<Product> products = jdbc.sql("""
                SELECT p.product_id, d.name, p.model, p.price, p.quantity FROM dd_product p
                JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                WHERE p.product_id IN (:ids)
                ORDER BY d.name
                """)
                .param("languageId", languageId).param("ids", productIds)
                .query((rs, n) -> new Product(rs.getInt("product_id"), rs.getString("name"), rs.getString("model"),
                        rs.getBigDecimal("price"), rs.getInt("quantity")))
                .list();

        Map<Integer, List<ProductOptionValueDto>> options = new HashMap<>();
        jdbc.sql("""
                SELECT v.product_id, v.product_option_id, v.product_option_value_id, COALESCE(od.name, '') AS option_name,
                       COALESCE(vd.name, '') AS value, v.quantity, v.price
                FROM dd_product_option_value v
                LEFT JOIN dd_option_description od ON od.option_id = v.option_id AND od.language_id = :languageId
                LEFT JOIN dd_option_value_description vd ON vd.option_value_id = v.option_value_id AND vd.language_id = :languageId
                WHERE v.product_id IN (:ids)
                ORDER BY v.product_option_value_id
                """)
                .param("languageId", languageId).param("ids", productIds)
                .query(rs -> {
                    options.computeIfAbsent(rs.getInt("product_id"), k -> new ArrayList<>()).add(new ProductOptionValueDto(
                            rs.getInt("product_option_id"), rs.getInt("product_option_value_id"), rs.getString("option_name"),
                            rs.getString("value"), rs.getInt("quantity"), rs.getBigDecimal("price")));
                });

        return products.stream()
                .map(p -> new ProductPickDto(p.id(), p.name(), p.model(), p.price(), p.quantity(), options.getOrDefault(p.id(), List.of())))
                .toList();
    }

    @Override
    public List<CustomerPickDto> searchCustomers(String term, int limit) {
        List<CustomerPickDto> rows = jdbc.sql("""
                SELECT customer_id, firstname, lastname, telephone, email, instagram, shipping_city, shipping_address_1,
                       shipping_address_2, order_id
                FROM dd_order
                WHERE order_status_id > 0 AND (LOCATE(:term, telephone) > 0 OR LOCATE(:term, email) > 0
                    OR LOCATE(:term, firstname) > 0 OR LOCATE(:term, lastname) > 0 OR LOCATE(:term, CONCAT(firstname, ' ', lastname)) > 0)
                ORDER BY order_id DESC
                LIMIT :limit
                """)
                .param("term", term).param("limit", limit * 5)
                .query((rs, n) -> new CustomerPickDto(rs.getInt("customer_id"), rs.getString("firstname"), rs.getString("lastname"),
                        rs.getString("telephone"), rs.getString("email"), rs.getString("instagram"), rs.getString("shipping_city"),
                        rs.getString("shipping_address_1"), rs.getString("shipping_address_2"), rs.getInt("order_id")))
                .list();

        // One customer, one row (the data of their last order)
        var byCustomer = new LinkedHashMap<String, CustomerPickDto>();
        for (CustomerPickDto c : rows) {
            String phoneKey = CustomerRisk.phoneKey(c.telephone());
            String key = phoneKey != null ? phoneKey : (c.firstname() + " " + c.lastname() + " " + c.email()).toLowerCase(Locale.ROOT);
            byCustomer.putIfAbsent(key, c);
        }
        return byCustomer.values().stream().limit(limit).toList();
    }

    @Override
    public List<CustomerOrder> findCustomerOrders(String phoneKey, String firstname, String lastname, String email, LocalDateTime since) {
        // The phone is written any way in the database ("+38 (067) 123-45-67"), so only the digits count, as on the old site
        record Row(int orderId, int statusId, String telephone, String firstname, String lastname, String email) {
        }
        List<Row> rows = jdbc.sql("""
                SELECT order_id, order_status_id, telephone, firstname, lastname, email FROM dd_order
                WHERE date_added >= :since AND order_status_id > 0 AND (
                    (:phoneKey IS NOT NULL
                        AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telephone, '+', ''), '-', ''), ' ', ''), '(', ''), ')', '')
                            LIKE CONCAT('%', :phoneKey))
                    OR (:firstname IS NOT NULL AND :lastname IS NOT NULL AND firstname = :firstname AND lastname = :lastname)
                    OR (:email IS NOT NULL AND email = :email))
                ORDER BY date_added DESC
                """)
                .param("since", since).param("phoneKey", phoneKey).param("firstname", firstname).param("lastname", lastname)
                .param("email", email)
                .query((rs, n) -> new Row(rs.getInt("order_id"), rs.getInt("order_status_id"), rs.getString("telephone"),
                        rs.getString("firstname"), rs.getString("lastname"), rs.getString("email")))
                .list();

        return rows.stream()
                .filter(o -> (phoneKey != null && phoneKey.equals(CustomerRisk.phoneKey(o.telephone())))
                        || (firstname != null && lastname != null && same(o.firstname(), firstname) && same(o.lastname(), lastname))
                        || (email != null && same(o.email(), email)))
                .map(o -> new CustomerOrder(o.orderId(), o.statusId()))
                .toList();
    }

    @Override
    public OrderMethodsDto listMethods() {
        return new OrderMethodsDto(methods("shipping"), methods("payment"));
    }

    private List<OrderMethodDto> methods(String kind) {
        // kind is "shipping" or "payment", never user input
        return jdbc.sql("""
                SELECT %1$s_code AS code, %1$s_method AS name FROM dd_order WHERE %1$s_method <> ''
                GROUP BY %1$s_code, %1$s_method
                ORDER BY COUNT(*) DESC
                """.formatted(kind))
                .query((rs, n) -> new OrderMethodDto(rs.getString("code"), rs.getString("name")))
                .list();
    }

    @Override
    public boolean sourceExists(int sourceId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_order_source WHERE order_source_id = :id").param("id", sourceId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public int create(NewOrderRecord order) {
        CreateOrderRequest r = order.request();
        // The store fields (name, currency, country, address format) come from the OpenCart settings, which we do not have,
        // so they are taken from the site's last order
        Map<String, Object> template = jdbc.sql("""
                SELECT invoice_prefix, store_id, store_name, store_url, language_id, currency_id, currency_code, currency_value,
                       shipping_country, shipping_country_id, shipping_zone, shipping_zone_id, shipping_address_format
                FROM dd_order ORDER BY order_id DESC LIMIT 1
                """)
                .query().listOfRows().stream().findFirst().orElse(Map.of());

        Map<String, Object> params = new HashMap<>();
        params.put("invoicePrefix", template.getOrDefault("invoice_prefix", ""));
        params.put("storeId", template.getOrDefault("store_id", 0));
        params.put("storeName", template.getOrDefault("store_name", ""));
        params.put("storeUrl", template.getOrDefault("store_url", ""));
        params.put("languageId", template.getOrDefault("language_id", languageId));
        params.put("currencyId", template.getOrDefault("currency_id", 0));
        params.put("currencyCode", template.getOrDefault("currency_code", "UAH"));
        params.put("currencyValue", template.getOrDefault("currency_value", BigDecimal.ONE));
        params.put("country", template.getOrDefault("shipping_country", "Україна"));
        params.put("countryId", template.getOrDefault("shipping_country_id", UKRAINE_COUNTRY_ID));
        params.put("zone", template.getOrDefault("shipping_zone", ""));
        params.put("zoneId", template.getOrDefault("shipping_zone_id", 0));
        params.put("addressFormat", template.getOrDefault("shipping_address_format", ""));
        params.put("firstname", text(r.firstname()));
        params.put("lastname", text(r.lastname()));
        params.put("email", text(r.email()));
        params.put("telephone", text(r.telephone()));
        params.put("instagram", text(r.instagram()));
        params.put("city", text(r.city()));
        params.put("address1", text(r.address1()));
        params.put("address2", text(r.address2()));
        params.put("house", text(r.house()));
        params.put("flat", text(r.flat()));
        params.put("paymentMethod", text(r.paymentMethod()));
        params.put("paymentCode", text(r.paymentCode()));
        params.put("shippingMethod", text(r.shippingMethod()));
        params.put("shippingCode", text(r.shippingCode()));
        params.put("comment", text(r.comment()));
        params.put("total", order.draft().total());
        params.put("prepayment", order.draft().prepayment());
        params.put("sourceId", r.orderSourceId());
        params.put("now", order.createdAt());

        // As on the old site: the payment and the shipping address are the same.
        // OpenCart tables are MyISAM, without transactions: saved step by step (first the order, to get its id).
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_order (invoice_no, invoice_prefix, store_id, store_name, store_url, customer_id, customer_group_id,
                    firstname, lastname, email, telephone, instagram, fax, custom_field,
                    payment_firstname, payment_lastname, payment_company, payment_address_1, payment_address_2, payment_city,
                    payment_postcode, payment_country, payment_country_id, payment_zone, payment_zone_id, payment_address_format,
                    payment_custom_field, payment_method, payment_code,
                    shipping_firstname, shipping_lastname, shipping_company, shipping_address_1, shipping_address_2, shipping_city,
                    shipping_postcode, shipping_country, shipping_country_id, shipping_zone, shipping_zone_id, shipping_address_format,
                    shipping_custom_field, shipping_method, shipping_code, shipping_house, shipping_flat,
                    comment, total, prepayment, order_status_id, order_source_id, affiliate_id, commission, marketing_id, tracking,
                    language_id, currency_id, currency_code, currency_value, ip, forwarded_ip, user_agent, accept_language,
                    date_added, date_modified)
                VALUES (0, :invoicePrefix, :storeId, :storeName, :storeUrl, 0, 0,
                    :firstname, :lastname, :email, :telephone, :instagram, '', '[]',
                    :firstname, :lastname, '', :address1, :address2, :city,
                    '', :country, :countryId, :zone, :zoneId, :addressFormat,
                    '[]', :paymentMethod, :paymentCode,
                    :firstname, :lastname, '', :address1, :address2, :city,
                    '', :country, :countryId, :zone, :zoneId, :addressFormat,
                    '[]', :shippingMethod, :shippingCode, :house, :flat,
                    :comment, :total, :prepayment, 0, :sourceId, 0, 0, 0, '',
                    :languageId, :currencyId, :currencyCode, :currencyValue, '', '', 'NewErp', '',
                    :now, :now)
                """)
                .params(params)
                .update(key);
        int orderId = key.getKey().intValue();

        addLines(orderId, text(r.shippingMethod()), order.draft(), order.lines());
        return orderId;
    }

    @Override
    public Optional<OrderCompositionDto> getComposition(int orderId) {
        record Order(int statusId, BigDecimal prepayment, BigDecimal total) {
        }
        Optional<Order> order = jdbc.sql("SELECT order_status_id, prepayment, total FROM dd_order WHERE order_id = :id")
                .param("id", orderId)
                .query((rs, n) -> new Order(rs.getInt("order_status_id"), rs.getBigDecimal("prepayment"), rs.getBigDecimal("total")))
                .optional();
        if (order.isEmpty()) {
            return Optional.empty();
        }

        record Product(int orderProductId, int productId, String name, String model, int quantity, BigDecimal price) {
        }
        List<Product> products = jdbc.sql("""
                SELECT order_product_id, product_id, name, model, quantity, price FROM dd_order_product
                WHERE order_id = :id ORDER BY order_product_id
                """)
                .param("id", orderId)
                .query((rs, n) -> new Product(rs.getInt("order_product_id"), rs.getInt("product_id"), rs.getString("name"),
                        rs.getString("model"), rs.getInt("quantity"), rs.getBigDecimal("price")))
                .list();

        // The size is the line's first option (the new order form writes one)
        record Option(int productOptionValueId, String value) {
        }
        Map<Integer, Option> options = new HashMap<>();
        jdbc.sql("SELECT order_product_id, product_option_value_id, value FROM dd_order_option WHERE order_id = :id ORDER BY order_option_id")
                .param("id", orderId)
                .query(rs -> {
                    options.putIfAbsent(rs.getInt("order_product_id"), new Option(rs.getInt("product_option_value_id"), rs.getString("value")));
                });

        Map<String, BigDecimal> totals = new HashMap<>();
        jdbc.sql("SELECT code, value FROM dd_order_total WHERE order_id = :id").param("id", orderId)
                .query(rs -> {
                    totals.merge(rs.getString("code"), rs.getBigDecimal("value"), BigDecimal::add);
                });

        Map<Integer, ProductPickDto> catalog = getProducts(products.stream().map(Product::productId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductPickDto::id, p -> p));

        List<OrderCompositionLine> lines = products.stream().map(p -> {
            Option option = options.get(p.orderProductId());
            ProductPickDto product = catalog.get(p.productId());
            return new OrderCompositionLine(p.productId(), option == null ? null : option.productOptionValueId(), p.name(), p.model(),
                    option == null ? "" : option.value(), p.quantity(), p.price(), product == null ? List.of() : product.options());
        }).toList();

        // OpenCart writes the coupon discount with a minus
        return Optional.of(new OrderCompositionDto(orderId, order.get().statusId(), lines,
                totals.getOrDefault("shipping", BigDecimal.ZERO),
                totals.getOrDefault("coupon", BigDecimal.ZERO).negate(),
                order.get().prepayment(),
                order.get().total()));
    }

    @Override
    public void replaceLines(int orderId, OrderDraft draft, List<NewOrderLineRecord> lines, LocalDateTime now) {
        String shippingMethod = jdbc.sql("SELECT shipping_method FROM dd_order WHERE order_id = :id").param("id", orderId)
                .query(String.class).single();

        // Like the old site's editOrder: the lines, options and totals are deleted and written again (MyISAM, no transaction)
        jdbc.sql("DELETE FROM dd_order_option WHERE order_id = :id").param("id", orderId).update();
        jdbc.sql("DELETE FROM dd_order_product WHERE order_id = :id").param("id", orderId).update();
        jdbc.sql("DELETE FROM dd_order_total WHERE order_id = :id").param("id", orderId).update();
        addLines(orderId, shippingMethod, draft, lines);
        jdbc.sql("UPDATE dd_order SET total = :total, prepayment = :prepayment, date_modified = :now WHERE order_id = :id")
                .param("total", draft.total()).param("prepayment", draft.prepayment()).param("now", now).param("id", orderId)
                .update();
    }

    private void addLines(int orderId, String shippingMethod, OrderDraft draft, List<NewOrderLineRecord> lines) {
        for (NewOrderLineRecord line : lines) {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO dd_order_product (order_id, product_id, name, model, quantity, price, total, tax, reward)
                    VALUES (:orderId, :productId, :name, :model, :quantity, :price, :total, 0, 0)
                    """)
                    .param("orderId", orderId).param("productId", line.line().productId()).param("name", line.name())
                    .param("model", line.model()).param("quantity", line.line().quantity()).param("price", line.line().price())
                    .param("total", line.line().total())
                    .update(key);

            if (line.option() != null) {
                // type "radio", as the size lines the site writes; to check against dd_option.type
                jdbc.sql("""
                        INSERT INTO dd_order_option (order_id, order_product_id, product_option_id, product_option_value_id, name, value, type)
                        VALUES (:orderId, :orderProductId, :optionId, :valueId, :name, :value, 'radio')
                        """)
                        .param("orderId", orderId).param("orderProductId", key.getKey().intValue())
                        .param("optionId", line.option().productOptionId()).param("valueId", line.option().productOptionValueId())
                        .param("name", line.option().optionName()).param("value", line.option().value())
                        .update();
            }
        }

        int sort = 0;
        addTotal(orderId, "sub_total", "Сума", draft.subTotal(), sort++);
        addTotal(orderId, "shipping", shippingMethod, draft.shippingCost(), sort++);
        if (draft.discount().signum() > 0) {
            addTotal(orderId, "coupon", "Знижка", draft.discount().negate(), sort++);
        }
        addTotal(orderId, "total", "Всього", draft.total(), sort);
    }

    private void addTotal(int orderId, String code, String title, BigDecimal value, int sortOrder) {
        jdbc.sql("INSERT INTO dd_order_total (order_id, code, title, value, sort_order) VALUES (:orderId, :code, :title, :value, :sort)")
                .param("orderId", orderId).param("code", code).param("title", title).param("value", value).param("sort", sortOrder)
                .update();
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean same(String a, String b) {
        return a.strip().equalsIgnoreCase(b);
    }
}
